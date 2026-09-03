import groovy.transform.Field

@Field Set executedComposeKeys = [] as Set
@Field Set copiedComposeKeys = [] as Set
// Host basina bir kez okunan `docker compose version` ciktisi (build omru boyunca cache'lenir)
@Field Map composeVersionByHost = [:]

// "v2.24.5" -> 2024005 : karsilastirilabilir tek sayi. Parse edilemezse 0.
def versionKey(String v) {
    def parts = (v ?: '').replaceAll('^[vV]', '').split('[^0-9]+').findAll { it }
    def a = parts.size() > 0 ? parts[0].toInteger() : 0
    def b = parts.size() > 1 ? parts[1].toInteger() : 0
    def c = parts.size() > 2 ? parts[2].toInteger() : 0
    return a * 1000000 + b * 1000 + c
}

// `docker compose up --wait` Compose >= 2.1.1, `--wait-timeout` >= 2.17 gerektiriyor.
// Daha eski bir host'ta bu bayraklar "unknown flag" ile HER deploy'u kirardi. Host'un surumu
// bir kez okunup, bayraklar yalnizca desteklendiginde ekleniyor; desteklenmiyorsa sessizce
// eski davranisa (duz `up -d`) donuluyor. Surum okunamazsa da guvenli tarafta kaliniyor.
def resolveComposeWaitArgs(String sshUser, def sshPort, String deployIp, def waitTimeout) {
    def hostKey = "${sshUser}@${deployIp}:${sshPort}"
    if (!composeVersionByHost.containsKey(hostKey)) {
        // Bu metot call() disinda; SSH_OPTS orada tanimli oldugu icin burada yeniden alinir.
        def sshOpts = sshOptions()
        def detected = sh(
            script: "ssh -p ${sshPort} ${sshOpts} ${sshUser}@${deployIp} 'docker compose version --short 2>/dev/null' || true",
            returnStdout: true
        ).trim()
        composeVersionByHost[hostKey] = detected
        if (detected) {
            echo "  ℹ️  ${deployIp} docker compose sürümü: ${detected}"
        } else {
            echo "  ⚠️  ${deployIp} docker compose sürümü okunamadı → --wait kullanılmayacak"
        }
    }
    def key = versionKey(composeVersionByHost[hostKey])
    if (key >= versionKey('2.17.0')) { return " --wait --wait-timeout ${waitTimeout}" }
    if (key >= versionKey('2.1.1'))  { return " --wait" }
    return ""
}

def call(Map config) {
    def serviceName = config.serviceName
    def SSH_OPTS = sshOptions()
    def version = config.version
    def appName = config.appName
    def deployIp = config.deployIp
    def nexusUrl = config.nexusUrl
    def nexusRegistryUrl = config.nexusRegistryUrl ?: env.NEXUS_REGISTRY_URL ?: nexusUrl
    def registryPath = config.registryPath
    def dockerComposeFile = config.dockerComposeFile ?: 'docker-compose.yml'
    def dockerComposeRemoteFile = config.dockerComposeRemoteFile ?: dockerComposeFile
    def envFile = config.envFile
    def sshUser = config.sshUser ?: 'your-user'
    def sshPort = config.sshPort ?: 22
    def shouldPull = config.shouldPull ?: false
    def imageName = config.imageName ?: serviceName
    def extraFiles = config.extraFiles ?: [] // [{src: 'nginx.conf', dest: '/app/appname/nginx.conf'}, ...]
    def extraEnvVars = config.extraEnvVars ?: [:] // [{key: 'DB_PASS', value: 'secret'}, ...] veya [DB_PASS: 'secret', ...]
    def runCompose = (config.runCompose != null ? config.runCompose : true)
    // opsiyonel: deploy host'unda hiçbir container'ın kullanmadığı TÜM imajları siler (varsayılan kapalı)
    def pruneImages = (config.pruneImages != null ? config.pruneImages.toString() == 'true' : false)
    // `docker compose up --wait` için timeout (sn). Migration'ı uzun süren servisler yanlışlıkla fail olmasın diye geniş tutuldu.
    def composeWaitTimeout = config.composeWaitTimeout ?: 180
    // `--wait`, isini yapip cikan (exit 0) container'larda hata dondurur — migration/init
    // container'i olan projeler bunu kapatmak icin composeWait: false verir.
    def composeWait = (config.composeWait != null) ? (config.composeWait.toString() == 'true') : true
    // Compose sonrasi dangling imaj temizligi. Host geneli calisir; kapatmak icin false.
    def pruneDangling = (config.pruneDangling != null) ? (config.pruneDangling.toString() == 'true') : true
    def composeOnceKey = config.composeOnceKey ?: "${appName}@${deployIp}:${dockerComposeRemoteFile}"
    def shouldCopyComposeNow = !copiedComposeKeys.contains(composeOnceKey)
    def shouldRunComposeNow = runCompose && !executedComposeKeys.contains(composeOnceKey)
    
    echo "🚀 [${serviceName}] Deploy başlıyor..."
    
    // Docker push/pull adresi (registry portu, örn: 8090)
    def nexusRegistry = nexusRegistryUrl.replaceAll('^https?://', '')
    def nexusImageName = "${nexusRegistry}/repository/${registryPath}/${imageName}:${version}"
    
    // Hedef dizini hazırla
    sh """
        ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "sudo mkdir -p /app/${appName}/ && sudo chown -R ${sshUser}:${sshUser} /app/${appName}/"
    """

    if (shouldPull) {
        echo "📥 [${serviceName}] Imaj çekiliyor: ${nexusImageName} → ${imageName}:${version}"
        sh """
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker rmi ${imageName}:${version} || true"
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker pull ${nexusImageName}"
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker tag ${nexusImageName} ${imageName}:${version}"
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker rmi ${nexusImageName} || true"
        """
        echo "✅ [${serviceName}] Imaj çekildi ve tag'lendi: ${imageName}:${version}"
    } else {
        echo "⏭️ [${serviceName}] Pull atlandı (shouldPull=false)"
    }
    
    // Ek dosyalar (örn. nginx.conf) veya dizinler (örn. config/) kopyala
    // ÖNCE extraFiles kopyalanmalı çünkü Docker Compose bu dosyalara ihtiyaç duyuyor
    if (extraFiles && extraFiles.size() > 0) {
        echo "📁 ${extraFiles.size()} adet extra file/dizin kopyalanacak..."
    } else {
        echo "ℹ️ extraFiles boş veya tanımlı değil"
    }
    
    extraFiles.each { fileCfg ->
        // Map veya string destekle (ör: "- backend" ya da "src: backend, dest: /app/..")
        def src = null
        def destPath = null

        if (fileCfg instanceof Map) {
            src = (fileCfg.src ?: fileCfg.source ?: fileCfg.path)?.toString()
            destPath = (fileCfg.dest ?: fileCfg.destination ?: fileCfg.target)?.toString()
        } else {
            src = fileCfg?.toString()
        }

        if (!src) {
            return
        }

        // basename icin ayri bir `sh` acmaya gerek yok — Groovy tarafinda hesaplanir.
        // (Sandbox'ta new File yasak oldugu icin string islemi kullaniliyor.)
        if (!destPath) {
            def baseName = src.replaceAll(/\/+$/, '').tokenize('/').last()
            destPath = "/app/${appName}/${baseName}"
        }

        // Varlik kontrolu Jenkins yerlesigi ile — kabuk acilmiyor.
        if (!fileExists(src)) {
            // Dosya yoksa, .env dosyası için Git'ten geri almayı dene veya oluştur
            if (src.endsWith('.env')) {
                echo "⚠️ ${src} bulunamadı, Git'ten geri alınmaya çalışılıyor..."
                def gitCheckoutResult = 1
                withEnv(["DS_ENV_FILE=${src}"]) {
                    gitCheckoutResult = sh(script: 'git checkout HEAD -- "$DS_ENV_FILE" 2>&1', returnStatus: true)
                }
                if (gitCheckoutResult == 0 && fileExists(src)) {
                    echo "✅ ${src} Git'ten geri alındı"
                } else {
                    // Git'te de yoksa, .env dosyasını oluştur (VERSION ile)
                    echo "📄 ${src} Git'te de bulunamadı, VERSION ile oluşturuluyor..."
                    writeFile file: src, text: "# Environment variables\nVERSION=${version}\n"
                    echo "✅ ${src} oluşturuldu (VERSION=${version})"
                }
            } else {
                echo "❌ ${src} bulunamadı, kopyalama atlanıyor"
                return
            }
        }

        // Dizin mi dosya mi: bunun icin Jenkins yerlesigi yok, tek `sh` kaliyor.
        def isDir = false
        withEnv(["DS_SRC=${src}"]) {
            isDir = (sh(script: 'test -d "$DS_SRC"', returnStatus: true) == 0)
        }
        def srcType = isDir ? "dizin" : "dosya"
        echo "📦 Kaynak türü: ${srcType} (${src})"

        // Sunucudaki hedefin durumu TEK ssh ile ogrenilir (onceden iki ayri baglanti aciliyordu):
        //   0 = yok, 1 = dosya olarak var, 2 = dizin olarak var
        def remoteState = 0
        withEnv([
            "DS_SSH_TARGET=${sshUser}@${deployIp}",
            "DS_SSH_PORT=${sshPort}",
            "DS_SSH_OPTS=${SSH_OPTS}",
            "DS_DEST=${destPath}"
        ]) {
            remoteState = sh(
                script: 'ssh -p "$DS_SSH_PORT" $DS_SSH_OPTS "$DS_SSH_TARGET" "if [ -d \'$DS_DEST\' ]; then exit 2; elif [ -e \'$DS_DEST\' ]; then exit 1; else exit 0; fi"',
                returnStatus: true
            )
        }

        if (remoteState != 0) {
            def remoteIsDir = (remoteState == 2)
            def remoteType = remoteIsDir ? "dizin" : "dosya"

            // Kaynak ve hedef türleri uyumsuzsa, hedefi temizle (Docker mount hatalarını önler)
            if (isDir != remoteIsDir) {
                echo "⚠️ Sunucuda ${destPath} ${remoteType} olarak mevcut, ${srcType} kopyalamak için temizleniyor..."
                withEnv([
                    "DS_SSH_TARGET=${sshUser}@${deployIp}",
                    "DS_SSH_PORT=${sshPort}",
                    "DS_SSH_OPTS=${SSH_OPTS}",
                    "DS_DEST=${destPath}"
                ]) {
                    sh 'ssh -p "$DS_SSH_PORT" $DS_SSH_OPTS "$DS_SSH_TARGET" "rm -rf \'$DS_DEST\'"'
                }
            }
        }

        // Hedef path'i temizle (sonundaki / karakterlerini kaldır)
        def cleanDestPath = destPath.toString().replaceAll(/\/+$/, '')

        // Kaynağın türüne göre scp. Yollar tirnak icinde: bosluk iceren dizin adlari
        // onceden komutu bolup yanlis dosya kopyalanmasina yol aciyordu.
        withEnv([
            "DS_SSH_TARGET=${sshUser}@${deployIp}",
            "DS_SSH_PORT=${sshPort}",
            "DS_SSH_OPTS=${SSH_OPTS}",
            "DS_SRC=${src}",
            "DS_DEST=${cleanDestPath}"
        ]) {
            if (isDir) {
                sh 'scp -r -P "$DS_SSH_PORT" $DS_SSH_OPTS "$DS_SRC" "$DS_SSH_TARGET:$DS_DEST"'
            } else {
                sh 'scp -P "$DS_SSH_PORT" $DS_SSH_OPTS "$DS_SRC" "$DS_SSH_TARGET:$DS_DEST"'
            }
        }
        echo "✅ ${srcType} kopyalandı: ${src} → ${cleanDestPath}"
    }

    // .env dosyası oluştur/güncelle
    // envFile varsa kullan, yoksa VERSION veya extraEnvVars varsa otomatik oluştur
    def shouldCreateEnv = envFile || version || (extraEnvVars && extraEnvVars.size() > 0)
    
    echo "🔍 .env dosyası kontrolü: envFile=${envFile}, extraEnvVars.size()=${extraEnvVars?.size() ?: 0}, shouldCreateEnv=${shouldCreateEnv}"
    
    if (shouldCreateEnv) {
        def envFileContent = ""
        
        // Sistem ortam değişkenlerini filtrelemek için liste
        def systemEnvVarsToFilter = [
            'SYSTEMD_EXEC_PID',
            'JOURNAL_STREAM',
            'MEMORY_PRESSURE_WATCH',
            'MEMORY_PRESSURE_WRITE',
            'INVOCATION_ID',
            'JAVA_HOME'
        ]
        
        // .env içeriğinden sistem değişkenlerini temizleyen fonksiyon
        def filterSystemEnvVars = { content ->
            def filtered = content
            systemEnvVarsToFilter.each { varName ->
                def escapedVarName = varName.replaceAll(/[.+*?^$()\[\]{}|\\]/, '\\\\$0')
                if (filtered.contains("${varName}=")) {
                    filtered = filtered.replaceAll(/(?m)^${escapedVarName}=.*$/, '')
                }
            }
            // Boş satırları temizle (birden fazla boş satırı tek boş satıra indir)
            filtered = filtered.replaceAll(/\n{3,}/, '\n\n').trim()
            if (filtered && !filtered.endsWith('\n')) {
                filtered += '\n'
            }
            return filtered
        }
        
        // Eğer envFile belirtilmişse: sunucudaki mevcut .env'i base al, üstüne merge et
        if (envFile) {
            // Sunucudaki mevcut .env'i oku (önceki servislerin değişkenleri korunur).
            // Degerler withEnv ile KAPSAMLI olarak gecirilir; global env'e yazilmaz.
            def remoteEnvExists = false
            def remoteContent = ''
            withEnv([
                "DS_SSH_TARGET=${sshUser}@${deployIp}",
                "DS_SSH_PORT=${sshPort}",
                "DS_SSH_OPTS=${SSH_OPTS}",
                "DS_APP=${appName}"
            ]) {
                remoteEnvExists = sh(script: 'ssh -p "$DS_SSH_PORT" $DS_SSH_OPTS "$DS_SSH_TARGET" "test -f /app/$DS_APP/.env"', returnStatus: true) == 0
                if (remoteEnvExists) {
                    remoteContent = sh(script: 'ssh -p "$DS_SSH_PORT" $DS_SSH_OPTS "$DS_SSH_TARGET" "cat /app/$DS_APP/.env"', returnStdout: true).trim()
                }
            }
            if (remoteEnvExists) {
                envFileContent = filterSystemEnvVars(remoteContent)
                if (envFileContent && !envFileContent.endsWith('\n')) envFileContent += '\n'
                echo "📄 Sunucudaki mevcut .env merge base olarak okundu: /app/${appName}/.env"
            }

            // Yerel .env dosyasını oku
            def localContent = ""
            if (fileExists(envFile)) {
                localContent = readFile(envFile)
                echo "📄 Yerel .env okundu: ${envFile}"
            } else {
                def gitCheckoutResult = 1
                withEnv(["DS_ENV_FILE=${envFile}"]) {
                    gitCheckoutResult = sh(script: 'git checkout HEAD -- "$DS_ENV_FILE" 2>&1', returnStatus: true)
                }
                if (gitCheckoutResult == 0 && fileExists(envFile)) {
                    localContent = readFile(envFile)
                    echo "📄 Git'ten .env geri alındı: ${envFile}"
                } else {
                    echo "📄 Yerel ${envFile} bulunamadı, mevcut sunucu içeriği korunuyor"
                }
            }

            // Yerel değişkenleri base üstüne merge et (yerel kazanır, base silinmez)
            if (localContent) {
                localContent = filterSystemEnvVars(localContent)
                localContent.split('\n').each { line ->
                    def trimmed = line.trim()
                    if (!trimmed || trimmed.startsWith('#')) return
                    def eqIdx = trimmed.indexOf('=')
                    if (eqIdx > 0) {
                        def k = trimmed.substring(0, eqIdx).trim()
                        def v = trimmed.substring(eqIdx + 1)
                        def escapedK = k.replaceAll(/[.+*?^$()\[\]{}|\\]/, '\\\\$0')
                        def safeReplacement = java.util.regex.Matcher.quoteReplacement("${k}=${v}")
                        if ((envFileContent =~ /(?m)^${escapedK}=/).find()) {
                            envFileContent = envFileContent.replaceAll(/(?m)^${escapedK}=.*$/, safeReplacement)
                        } else {
                            envFileContent += "${k}=${v}\n"
                        }
                    }
                }
                echo "📄 ${envFile} merge edildi → /app/${appName}/.env"
            }
        } else {
            // envFile belirtilmemişse, sunucudaki mevcut .env dosyasını oku (başka kaynak yok)
            def remoteEnvExists = false
            def remoteRaw = ''
            withEnv([
                "DS_SSH_TARGET=${sshUser}@${deployIp}",
                "DS_SSH_PORT=${sshPort}",
                "DS_SSH_OPTS=${SSH_OPTS}",
                "DS_APP=${appName}"
            ]) {
                remoteEnvExists = sh(script: 'ssh -p "$DS_SSH_PORT" $DS_SSH_OPTS "$DS_SSH_TARGET" "test -f /app/$DS_APP/.env"', returnStatus: true) == 0
                if (remoteEnvExists) {
                    remoteRaw = sh(script: 'ssh -p "$DS_SSH_PORT" $DS_SSH_OPTS "$DS_SSH_TARGET" "cat /app/$DS_APP/.env"', returnStdout: true).trim()
                }
            }
            if (remoteEnvExists) {
                envFileContent = remoteRaw
                if (!envFileContent) {
                    envFileContent = "# Environment variables\n"
                } else {
                    envFileContent = filterSystemEnvVars(envFileContent)
                }
                echo "📄 Sunucudaki mevcut .env dosyası okundu ve sistem değişkenleri filtrelendi (envFile belirtilmemiş, mevcut içerik korunuyor)"
            } else {
                envFileContent = "# Environment variables\n"
                echo "📄 Yeni .env dosyası oluşturuluyor (envFile belirtilmemiş, sunucuda .env yok)"
            }
        }
        
        // VERSION'ı güncelle/ekle (yalnızca satır başındaki gerçek VERSION= eşleşsin)
        if ((envFileContent =~ /(?m)^VERSION=/).find()) {
            envFileContent = envFileContent.replaceAll(/(?m)^VERSION=.*$/, "VERSION=${version}")
        } else {
            envFileContent += "\nVERSION=${version}\n"
        }
        
        // Extra environment değişkenlerini ekle/güncelle (credentials dahil)
        if (extraEnvVars && extraEnvVars.size() > 0) {
            echo "📝 ${extraEnvVars.size()} adet credential .env dosyasına ekleniyor..."
            extraEnvVars.each { key, value ->
                if (key && value != null) {
                    def keyStr = key.toString()
                    
                    // Sistem değişkenlerini filtrele (ekleme)
                    if (systemEnvVarsToFilter.contains(keyStr)) {
                        echo "  🗑️ Sistem değişkeni atlandı (extraEnvVars'dan): ${keyStr}"
                        return
                    }
                    
                    def valueStr = value.toString()
                    
                    // Value'yu escape et (özel karakterler için)
                    def escapedValue = valueStr
                        .replace('\\', '\\\\')  // \ önce escape edilmeli
                        .replace('$', '\\$')
                        .replace('`', '\\`')
                        .replace('"', '\\"')
                        .replace('\n', '\\n')
                        .replace('\r', '')
                    
                    // Key'i regex için escape et
                    def escapedKey = keyStr.replaceAll(/[.+*?^$()\[\]{}|\\]/, '\\\\$0')
                    
                    // Değişken zaten varsa güncelle, yoksa ekle (satır başından kontrol et)
                    if ((envFileContent =~ /(?m)^${escapedKey}=/).find()) {
                        envFileContent = envFileContent.replaceAll(/(?m)^${escapedKey}=.*$/, "${keyStr}=${escapedValue}")
                        echo "  ✅ ${keyStr} güncellendi"
                    } else {
                        envFileContent += "${keyStr}=${escapedValue}\n"
                        echo "  ✅ ${keyStr} eklendi"
                    }
                }
            }
        } else {
            echo "⚠️ extraEnvVars boş veya null"
        }
        
        // Geçici .env dosyası oluştur
        def tempEnvFile = envFile ?: '.env.temp'
        writeFile file: tempEnvFile, text: envFileContent
        
        // Sunucuya kopyala
        sh """
            scp -P ${sshPort} ${SSH_OPTS} ${tempEnvFile} ${sshUser}@${deployIp}:/app/${appName}/.env
        """
        
        // Geçici dosyayı temizle (eğer otomatik oluşturulduysa)
        if (!envFile) {
            sh "rm -f ${tempEnvFile}"
        }
    }
    
    // docker-compose dosyasını kopyala (uzakta farklı bir isimle saklanabilir).
    // Kopyalama compose'u çalıştırmaktan bağımsızdır: aynı host'a deploy olan servislerden sadece
    // sonuncusu `up` çalıştırır, ama compose dosyası ilk servis sırasında yerine konmuş olmalıdır.
    if (shouldCopyComposeNow) {
        copiedComposeKeys << composeOnceKey
        sh """
            scp -P ${sshPort} ${SSH_OPTS} ${dockerComposeFile} ${sshUser}@${deployIp}:/app/${appName}/${dockerComposeRemoteFile}
        """
    } else {
        echo "⏭️ [${serviceName}] compose dosyası zaten kopyalandı (composeOnceKey=${composeOnceKey}) → tekrar atlandı"
    }

    if (shouldRunComposeNow) {
        executedComposeKeys << composeOnceKey
        // "down" kaldırıldı → tüm container'ları durdurup kaldırmak gereksiz kesinti yaratıyordu.
        // "up -d" zaten sadece config/image'i değişen container'ı yeniden oluşturur.
        // Sabit "sleep 15s" yerine "--wait": container'lar (healthcheck tanımlıysa healthy, yoksa
        // running) olana kadar beklenir; olmazsa komut hata döner ve Jenkins adımı durur.
        def waitArgs = composeWait ? resolveComposeWaitArgs(sshUser, sshPort, deployIp, composeWaitTimeout) : ""
        if (composeWait && !waitArgs) {
            echo "  ⚠️  --wait desteklenmiyor → `up -d` sonrası sağlık beklenmeyecek"
        }
        sh """
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "sudo chown -R ${sshUser}:${sshUser} /app/${appName}/"
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "cd /app/${appName} && docker compose -f ${dockerComposeRemoteFile} ${shouldCreateEnv ? '--env-file .env' : ''} up -d${waitArgs}"
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "cd /app/${appName}; docker compose logs --tail=100"
        """

        // Aynı tag (ör. test-v1.0) tekrar deploy edildiğinde eski container yeni imaja geçer ve eski
        // imaj hiçbir tag'e/container'a bağlı kalmaz (dangling) → burada temizlenir. "-a" YOK: başka
        // bir tag'e bağlı imajlar (elle saklanan eski version'lar) hiç etkilenmez.
        if (pruneDangling) {
            sh """
                ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker image prune -f || true"
            """
        }

        // pruneImages: opsiyonel. "-a" hiçbir container'ın kullanmadığı TÜM imajları siler (sadece
        // dangling değil) — eski version tag'lerinin sunucuda sınırsız birikmesini engeller. Jenkins
        // agent'ında değil yalnızca bu deploy host'unda çalışır; az önce ayağa kalkan container'lar
        // "kullanımda" sayıldığı için güvenlidir.
        if (pruneImages) {
            sh """
                ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker image prune -a -f || true"
            """
        }
    } else {
        echo "⏭️ [${serviceName}] runCompose=false → docker compose adımı atlandı"
    }
    
    echo "✅ [${serviceName}] Deploy tamamlandı"
}

