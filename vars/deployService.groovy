import groovy.transform.Field

@Field Set executedComposeKeys = [] as Set

def call(Map config) {
    def serviceName = config.serviceName
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
    env.DEPLOY_SSH_PORT = sshPort.toString()
    def shouldPull = config.shouldPull ?: false
    def imageName = config.imageName ?: serviceName
    def extraFiles = config.extraFiles ?: [] // [{src: 'nginx.conf', dest: '/app/appname/nginx.conf'}, ...]
    def extraEnvVars = config.extraEnvVars ?: [:] // [{key: 'DB_PASS', value: 'secret'}, ...] veya [DB_PASS: 'secret', ...]
    def runCompose = (config.runCompose != null ? config.runCompose : true)
    def composeOnceKey = config.composeOnceKey ?: "${appName}@${deployIp}:${dockerComposeRemoteFile}"
    def shouldRunComposeNow = runCompose && !executedComposeKeys.contains(composeOnceKey)
    
    echo "🚀 [${serviceName}] Deploy başlıyor..."
    
    // Docker push/pull adresi (registry portu, örn: 8090)
    def nexusRegistry = nexusRegistryUrl.replaceAll('^https?://', '')
    def nexusImageName = "${nexusRegistry}/repository/${registryPath}/${imageName}:${version}"
    
    // Hedef dizini hazırla
    sh """
        ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "sudo mkdir -p /app/${appName}/ && sudo chown -R ${sshUser}:${sshUser} /app/${appName}/"
    """

    if (shouldPull) {
        echo "📥 [${serviceName}] Imaj çekiliyor: ${nexusImageName} → ${imageName}:${version}"
        sh """
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker rmi ${imageName}:${version} || true"
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker pull ${nexusImageName}"
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker tag ${nexusImageName} ${imageName}:${version}"
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker rmi ${nexusImageName} || true"
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

        // Sandbox'ta new File yasak; isim için basename kullan
        if (!destPath) {
            env.DEPLOY_SRC_BASENAME = src
            def baseName = sh(script: 'basename "${DEPLOY_SRC_BASENAME}"', returnStdout: true).trim()
            destPath = "/app/${appName}/${baseName}"
        }

        // Kaynak dosya mı dizin mi kontrol et
        env.DEPLOY_SRC_CHECK = src
        def srcExists = sh(script: 'test -e "${DEPLOY_SRC_CHECK}"', returnStatus: true) == 0
        if (!srcExists) {
            // Dosya yoksa, .env dosyası için Git'ten geri almayı dene veya oluştur
            if (src.toString().endsWith('.env')) {
                echo "⚠️ ${src} bulunamadı, Git'ten geri alınmaya çalışılıyor..."
                env.DEPLOY_SRC_GIT = src
                def gitCheckoutResult = sh(script: 'git checkout HEAD -- "${DEPLOY_SRC_GIT}" 2>&1', returnStatus: true)
                if (gitCheckoutResult == 0 && sh(script: 'test -e "${DEPLOY_SRC_GIT}"', returnStatus: true) == 0) {
                    echo "✅ ${src} Git'ten geri alındı"
                } else {
                    // Git'te de yoksa, .env dosyasını oluştur (VERSION ile)
                    echo "📄 ${src} Git'te de bulunamadı, VERSION ile oluşturuluyor..."
                    def envContent = "# Environment variables\nVERSION=${version}\n"
                    writeFile file: src, text: envContent
                    echo "✅ ${src} oluşturuldu (VERSION=${version})"
                }
            } else {
                echo "❌ ${src} bulunamadı, kopyalama atlanıyor"
                return
            }
        }
        
        env.DEPLOY_SRC_ISDIR = src
        def isDir = sh(script: 'test -d "${DEPLOY_SRC_ISDIR}"', returnStatus: true) == 0
        def srcType = isDir ? "dizin" : "dosya"
        echo "📦 Kaynak türü: ${srcType} (${src})"
        
        // Sunucuda hedef path'in mevcut durumunu kontrol et
        env.DEPLOY_SSH_USER_CHECK = sshUser
        env.DEPLOY_IP_CHECK = deployIp
        env.DEPLOY_DEST_PATH_CHECK = destPath
        def remoteExists = sh(script: 'ssh -p ${DEPLOY_SSH_PORT} -o ConnectTimeout=20 "${DEPLOY_SSH_USER_CHECK}@${DEPLOY_IP_CHECK}" "test -e ${DEPLOY_DEST_PATH_CHECK}"', returnStatus: true) == 0
        if (remoteExists) {
            def remoteIsDir = sh(script: 'ssh -p ${DEPLOY_SSH_PORT} -o ConnectTimeout=20 "${DEPLOY_SSH_USER_CHECK}@${DEPLOY_IP_CHECK}" "test -d ${DEPLOY_DEST_PATH_CHECK}"', returnStatus: true) == 0
            def remoteType = remoteIsDir ? "dizin" : "dosya"
            
            // Kaynak ve hedef türleri uyumsuzsa, hedefi temizle
            if (isDir != remoteIsDir) {
                echo "⚠️ Sunucuda ${destPath} ${remoteType} olarak mevcut, ${srcType} kopyalamak için temizleniyor..."
                sh """
                    ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "rm -rf ${destPath}"
                """
            }
        }
        
        // Hedef path'i temizle (sonundaki / karakterlerini kaldır)
        def cleanDestPath = destPath.toString().replaceAll(/\/+$/, '')
        
        // Kaynağın türüne göre scp komutunu çalıştır
        def scpFlags = isDir ? "-r" : ""
        sh """
            scp ${scpFlags} -P ${sshPort} -o ConnectTimeout=20 ${src} ${sshUser}@${deployIp}:${cleanDestPath}
        """
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
            // Sunucudaki mevcut .env'i oku (önceki servislerin değişkenleri korunur)
            env.DEPLOY_SSH_USER_ENV = sshUser
            env.DEPLOY_IP_ENV = deployIp
            env.DEPLOY_APP_NAME_ENV = appName
            def remoteEnvExists = sh(script: 'ssh -p ${DEPLOY_SSH_PORT} -o ConnectTimeout=20 "${DEPLOY_SSH_USER_ENV}@${DEPLOY_IP_ENV}" "test -f /app/${DEPLOY_APP_NAME_ENV}/.env"', returnStatus: true) == 0
            if (remoteEnvExists) {
                def remoteContent = sh(script: 'ssh -p ${DEPLOY_SSH_PORT} -o ConnectTimeout=20 "${DEPLOY_SSH_USER_ENV}@${DEPLOY_IP_ENV}" "cat /app/${DEPLOY_APP_NAME_ENV}/.env"', returnStdout: true).trim()
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
                env.DEPLOY_ENV_FILE_GIT = envFile
                def gitCheckoutResult = sh(script: 'git checkout HEAD -- "${DEPLOY_ENV_FILE_GIT}" 2>&1', returnStatus: true)
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
                        if ((envFileContent =~ /(?m)^${escapedK}=/).find()) {
                            envFileContent = envFileContent.replaceAll(/(?m)^${escapedK}=.*$/, "${k}=${v}")
                        } else {
                            envFileContent += "${k}=${v}\n"
                        }
                    }
                }
                echo "📄 ${envFile} merge edildi → /app/${appName}/.env"
            }
        } else {
            // envFile belirtilmemişse, sunucudaki mevcut .env dosyasını oku (başka kaynak yok)
            env.DEPLOY_SSH_USER_ENV = sshUser
            env.DEPLOY_IP_ENV = deployIp
            env.DEPLOY_APP_NAME_ENV = appName
            def remoteEnvExists = sh(script: 'ssh -p ${DEPLOY_SSH_PORT} -o ConnectTimeout=20 "${DEPLOY_SSH_USER_ENV}@${DEPLOY_IP_ENV}" "test -f /app/${DEPLOY_APP_NAME_ENV}/.env"', returnStatus: true) == 0
            if (remoteEnvExists) {
                envFileContent = sh(script: 'ssh -p ${DEPLOY_SSH_PORT} -o ConnectTimeout=20 "${DEPLOY_SSH_USER_ENV}@${DEPLOY_IP_ENV}" "cat /app/${DEPLOY_APP_NAME_ENV}/.env"', returnStdout: true).trim()
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
            scp -P ${sshPort} -o ConnectTimeout=20 ${tempEnvFile} ${sshUser}@${deployIp}:/app/${appName}/.env
        """
        
        // Geçici dosyayı temizle (eğer otomatik oluşturulduysa)
        if (!envFile) {
            sh "rm -f ${tempEnvFile}"
        }
    }
    
    // docker-compose dosyasını kopyala (uzakta farklı bir isimle saklanabilir)
    if (runCompose && !shouldRunComposeNow) {
        echo "⏭️ [${serviceName}] compose daha önce çalıştı (composeOnceKey=${composeOnceKey}) → tekrar atlandı"
    }

    if (shouldRunComposeNow) {
        executedComposeKeys << composeOnceKey
        sh """
            scp -P ${sshPort} -o ConnectTimeout=20 ${dockerComposeFile} ${sshUser}@${deployIp}:/app/${appName}/${dockerComposeRemoteFile}
        """
        
        // Docker compose up
        sh """
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "cd /app/${appName}; docker compose down || true"
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "sudo chown -R ${sshUser}:${sshUser} /app/${appName}/"
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "cd /app/${appName} && docker compose -f ${dockerComposeRemoteFile} ${shouldCreateEnv ? '--env-file .env' : ''} up -d"
            sleep 15s
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "cd /app/${appName}; docker compose logs --tail=100"
        """
    } else {
        echo "⏭️ [${serviceName}] runCompose=false → docker compose adımı atlandı"
    }
    
    echo "✅ [${serviceName}] Deploy tamamlandı"
}

