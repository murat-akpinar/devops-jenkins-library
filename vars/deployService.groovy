import groovy.transform.Field

@Field Set executedComposeKeys = [] as Set
@Field Set copiedComposeKeys = [] as Set
// Host başına bir kez okunan `docker compose version` çıktısı (build ömrü boyunca cache'lenir)
@Field Map composeVersionByHost = [:]
// Host basina bu build'de deploy edilen imajlar: "kullanici@ip" -> [[name: ..., version: ...]]
// compose up host basina BIR KEZ, son servisle calisiyor; eski tag temizligi de orada yapiliyor.
// O ana kadar hangi servislerin deploy edildigini bilmek icin cagrilar arasi biriktiriliyor.
@Field Map deployedImagesByHost = [:]

// bindMountIzinleriniAyarla'nin host'ta calistirdigi kabuk betigi.
@Field String BIND_MOUNT_ACL_SCRIPT = '''
set -u
# tr_TR collation'inda [A-Za-z] araligi 'i' harfini disliyor; regex'ler bozuluyor.
export LC_ALL=C
cd "/app/$APP" 2>/dev/null || exit 0
[ -f "$COMPOSE" ] || exit 0

# Yalnizca log dizinleri: "- ./dizin:/var/log/..." . Kapsam bilerek dar tutuldu —
# "./default.conf:/etc/nginx/conf.d/default.conf" gibi DOSYA mount'larina mkdir calistirmak
# dosyanin yerine dizin yaratip servisi bozar.
DIRS=$(grep -oE "^[[:space:]]*-[[:space:]]*\\./[[:alnum:]_.-]+:/var/log/[[:alnum:]_./-]*" "$COMPOSE" \\
       | sed -E "s#^[[:space:]]*-[[:space:]]*\\./##; s#:/var/log/.*##" | sort -u)
[ -n "$DIRS" ] || exit 0

for d in $DIRS; do sudo mkdir -p "$d"; done

if ! command -v setfacl >/dev/null 2>&1; then
    echo "  UYARI: host'ta setfacl yok (acl paketi kurulu degil)."
    echo "         Non-root container bind mount'a yazamaz: sudo apt-get install -y acl"
    exit 0
fi

for img in $IMAGES; do
    u=$(docker image inspect --format "{{.Config.User}}" "$img" 2>/dev/null) || continue
    [ -n "$u" ] || continue
    u=${u%%:*}
    case "$u" in root|0) continue ;; esac
    case "$u" in
        *[!0-9]*) u=$(docker run --rm --entrypoint id "$img" -u "$u" 2>/dev/null | tr -d "\\r\\n") ;;
    esac
    case "$u" in ""|*[!0-9]*) echo "  UYARI: $img calisma kullanicisi cozulemedi, ACL atlandi"; continue ;; esac
    ok=1
    for d in $DIRS; do
        sudo setfacl -R -m "u:$u:rwX" "$d" || ok=0
        sudo setfacl -d -m "u:$u:rwx" "$d" || ok=0
    done
    if [ "$ok" = 1 ]; then
        echo "  ACL verildi: uid $u -> $(echo $DIRS | tr '\\n' ' ')"
    else
        echo "  UYARI: uid $u icin ACL verilemedi ($(echo $DIRS | tr '\\n' ' ')); non-root container yazamayabilir"
    fi
done
'''

// Bind mount edilen host dizinlerini olusturur ve non-root container'in uid'ine ACL verir.
//
// Neden gerekli: bind mount, imajdaki dizin sahipligini EZER. Docker host dizinini root:root
// yaratir; container'daki surec non-root ise (USER nginx -> uid 101) iceriye yazamaz ve
// "could not open error log file ... (13: Permission denied)" ile aciliste olur. Healthcheck
// hic baslamadigi icin deploy da duser.
//
// Neden chown degil ACL: bu dosya her deploy'da "chown -R <sshUser>" calistiriyor; chown ile
// verilen sahiplik her seferinde geri alinirdi. ACL kaydi genisletilmis ozniteliklerde durur,
// chown onu silmez.
//
// Hicbir Jenkinsfile/services.yml degisikligi gerekmez: dizin adlari compose dosyasindan,
// uid ise imajin kendisinden (.Config.User) okunur.
def bindMountIzinleriniAyarla(String sshOpts, String sshTarget, String appName, String composeFile, List images) {
    def imageList = (images ?: []).collect { "${it.name}:${it.version}" }.join(' ')
    if (!imageList) { return }
    def header = "APP='" + appName + "'\nCOMPOSE='" + composeFile + "'\nIMAGES='" + imageList + "'\n"
    writeFile file: '.deploy-bindmount-acl.sh', text: header + BIND_MOUNT_ACL_SCRIPT
    sh "ssh ${sshOpts} ${sshTarget} 'bash -s' < .deploy-bindmount-acl.sh || true"
}

// "v2.24.5" -> 2024005 : karşılaştırılabilir tek sayi. Parse edilemezse 0.
def versionKey(String v) {
    def parts = (v ?: '').replaceAll('^[vV]', '').split('[^0-9]+').findAll { it }
    def a = parts.size() > 0 ? parts[0].toInteger() : 0
    def b = parts.size() > 1 ? parts[1].toInteger() : 0
    def c = parts.size() > 2 ? parts[2].toInteger() : 0
    return a * 1000000 + b * 1000 + c
}

// Deploy host'unda, SADECE bu build'de deploy edilen imajlarin eski surum tag'lerini siler.
// `docker image prune -a` yerine bu: prune host'taki her bosta imaji (komsu uygulamalar,
// aquasec/trivy, base imajlar) siliyordu. Burada dokunulan tek sey "<imaj>:<baska surum>".
//
// Filtreleme ssh'in OTESINDE degil, agent tarafinda yapiliyor: grep/awk ifadelerini ssh'in
// cift tirnagindan gecirmek `$` ve tek tirnak kacislari yuzunden kirilgan. Uzak tarafa yalnizca
// duz `docker rmi -f <tag> <tag>` gidiyor.
def eskiSurumTagleriniSil(String sshOpts, String sshTarget, List images) {
    if (!images) { return }
    def listed = sh(
        script: "ssh ${sshOpts} ${sshTarget} \"docker images --format '{{.Repository}}:{{.Tag}}'\" || true",
        returnStdout: true,
    ).trim()
    if (!listed) { return }

    // Bir container'in (CALISAN ya da DURMUS) referans verdigi tag'e ASLA dokunulmuyor.
    // `docker rmi -f` calisan container'in imajini hata vermeden UNTAG ediyor: container
    // kosmaya devam ediyor ama `docker ps` imaji artik ID olarak gosteriyor ve o stack bir
    // daha `compose up` edilemiyor (tag yerelde yok, kayitsiz referans pull edilemez).
    // Ayni host'ta ayni uygulamanin test ve prod'u duruyorsa (app_backend:test-v1.1 +
    // :prod-v3.0) test deploy'u prod'u boyle sessizce kirardi.
    def kullanilan = sh(
        script: "ssh ${sshOpts} ${sshTarget} \"docker ps -a --format '{{.Image}}'\" || true",
        returnStdout: true,
    ).trim().split('\n').collect { it.trim() }.findAll { it } as Set

    def silinecek = [] as Set
    listed.split('\n').each { satir ->
        def tag = satir.trim()
        def idx = tag.lastIndexOf(':')
        if (!tag || idx < 1) { return }
        def repo = tag.substring(0, idx)
        def surum = tag.substring(idx + 1)
        if (surum == '<none>') { return }   // dangling: zaten `prune -f` topluyor
        // Ayni imaj hem ciplak adla hem <nexus>/repository/<repo>/<ad> onekiyle durabiliyor.
        def sonParca = repo.contains('/') ? repo.substring(repo.lastIndexOf('/') + 1) : repo
        if (kullanilan.contains(tag)) { return }
        images.each { im ->
            if (sonParca == im.name && surum != im.version) { silinecek << tag }
        }
    }

    if (!silinecek) {
        echo "  ℹ️ Eski sürüm tag'i yok, temizlenecek bir şey bulunamadı"
        return
    }
    echo "  🗑️ Eski sürüm tag'leri siliniyor: ${silinecek.join(', ')}"
    sh """
        ssh ${sshOpts} ${sshTarget} "docker rmi -f ${silinecek.join(' ')}" || true
    """
}

// `docker compose up --wait` Compose >= 2.1.1, `--wait-timeout` >= 2.17 gerektiriyor.
// Daha eski bir host'ta bu bayraklar "unknown flag" ile HER deploy'u kırardı. Host'un sürümü
// bir kez okunup, bayraklar yalnızca desteklendiğinde ekleniyor; desteklenmiyorsa sessizce
// eski davranışa (düz `up -d`) dönülüyor. Sürüm okunamazsa da güvenli tarafta kalınıyor.
def resolveComposeWaitArgs(String sshUser, def sshPort, String deployIp, def waitTimeout) {
    def hostKey = "${sshUser}@${deployIp}:${sshPort}"
    if (!composeVersionByHost.containsKey(hostKey)) {
        // Bu metot call() dışında; SSH_OPTS orada tanımlı olduğu için burada yeniden alınır.
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

// `up --wait` bir container "unhealthy" olunca --wait-timeout'u beklemeden ANINDA hata dönüyor.
// Docker ise unhealthy → healthy geçişine izin veriyor (tek başarılı kontrol yeter). İlk deploy'da
// migration vb. yüzünden start_period'u aşan yavaş servisler bu yüzden "unhealthy" deyip fail
// oluyordu, oysa birkaç saniye sonra healthy oluyorlardı (2.-3. denemede geçmesi bundan).
// --wait hata dönerse kalan süre boyunca durumu kendimiz yokluyoruz; ancak süre bittiğinde hâlâ
// hazır olmayan ya da hiç başlamayan/non-zero exit eden container varsa false dönüyoruz.
def waitUntilHealthy(String sshOpts, String sshTarget, String composeBase, long deadlineMs) {
    def lastReport = null
    while (true) {
        def out = sh(
            script: "ssh ${sshOpts} ${sshTarget} '${composeBase} ps -aq | xargs -r docker inspect -f \"{{.Name}} {{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} {{.State.ExitCode}}\"' || true",
            returnStdout: true
        ).trim()
        def notReady = []
        def fatal = []
        for (line in out.readLines()) {
            def f = line.trim().split(/\s+/)
            if (f.size() < 4) { continue }
            def name = f[0].replaceAll('^/', '')
            def status = f[1]
            def health = f[2]
            def exitCode = f[3]
            if (status == 'exited' && exitCode == '0') { continue }
            if (status == 'running' && (health == 'healthy' || health == 'none')) { continue }
            if (status == 'dead' || status == 'created' || (status == 'exited' && exitCode != '0')) {
                fatal << "${name}(${status}, exit=${exitCode})"
            } else {
                notReady << "${name}(${status}/${health})"
            }
        }
        if (out && !fatal && !notReady) {
            echo "  ✅ Tüm container'lar hazır"
            return true
        }
        if (fatal) {
            echo "  ❌ Başlamayan/çöken container: ${fatal.join(', ')}"
            return false
        }
        def remaining = ((deadlineMs - System.currentTimeMillis()) / 1000).toInteger()
        def report = notReady.join(', ')
        if (report != lastReport) {
            echo "  ⏳ Hazır değil: ${report ?: '(container bulunamadı)'} — kalan ~${Math.max(remaining, 0)}s"
            lastReport = report
        }
        if (remaining <= 0) { return false }
        sleep(time: 5, unit: 'SECONDS')
    }
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
    // SSH kullanıcısı/portu bilerek globalConfig'ten okunmuyor: globalConfig tüm
    // projelerin paylaştığı tek dosya, bu değerler ise proje ve ortam bazında değişiyor.
    // Doğru yer services.yml (environments.<ortam>.ssh_user / services[].ssh_user).
    def sshUser = config.sshUser ?: 'your-user'
    def sshPort = config.sshPort ?: 22
    def shouldPull = config.shouldPull ?: false
    def imageName = config.imageName ?: serviceName
    def extraFiles = config.extraFiles ?: [] // [{src: 'nginx.conf', dest: '/app/appname/nginx.conf'}, ...]
    def extraEnvVars = config.extraEnvVars ?: [:] // [{key: 'DB_PASS', value: 'secret'}, ...] veya [DB_PASS: 'secret', ...]
    def runCompose = (config.runCompose != null ? config.runCompose : true)
    // Varsayılan AÇIK: eski sürüm tag'leri deploy sunucularında sınırsız birikiyordu. Temizlik
    // compose up'tan SONRA çalışır ve YALNIZCA bu build'de deploy edilen imajların başka sürüm
    // tag'lerine dokunur — komşu uygulamalara, base imajlara, araç imajlarına değil.
    // Rollback engellenmiyor: deploy zaten yereldeki tag'i silip Nexus'tan çekiyor.
    def pruneImages = (config.pruneImages != null ? config.pruneImages.toString() == 'true' : true)
    // Varsayılan AÇIK. Compose içinde DB/Redis gibi her deploy'da yenilenmesi istenmeyen bir
    // servis varsa proje forceRecreate: false geçer (o zaman nginx conf'unda resolver gerekir).
    def forceRecreate = (config.forceRecreate != null ? config.forceRecreate.toString() == 'true' : true)
    // compose up --wait için toplam bekleme süresi (sn). Yavaş açılan servisler (ilk deploy'da
    // migration vb.) healthcheck start_period'una göre bunu Jenkinsfile'dan büyütebilir.
    def composeWaitTimeout = (config.composeWaitTimeout ?: 180).toString().toInteger()
    // `--wait`, işini yapıp çıkan (exit 0) container'larda hata döndürür — migration/init
    // container'ı olan projeler bunu kapatmak için composeWait: false verir.
    def composeWait = (config.composeWait != null) ? (config.composeWait.toString() == 'true') : true
    // Compose sonrası dangling imaj temizliği. Host geneli çalışır; kapatmak için false.
    def pruneDangling = (config.pruneDangling != null) ? (config.pruneDangling.toString() == 'true') : true
    def composeOnceKey = config.composeOnceKey ?: "${appName}@${deployIp}:${dockerComposeRemoteFile}"
    def shouldCopyComposeNow = !copiedComposeKeys.contains(composeOnceKey)
    def shouldRunComposeNow = runCompose && !executedComposeKeys.contains(composeOnceKey)
    
    echo "🚀 [${serviceName}] Deploy başlıyor..."

    // Eski tag temizliği compose up'tan sonra, host başına TEK SEFER çalışıyor; o ana kadar
    // bu host'a hangi imajların deploy edildiğini bilmek için çağrı çağrı biriktiriliyor.
    // shouldPull'dan bağımsız: imaj çekilmese de compose o tag'i kullanıyor, korunmalı.
    def hostKey = "${sshUser}@${deployIp}:${sshPort}".toString()
    deployedImagesByHost.get(hostKey, []) << [name: imageName, version: version]
    // Yardımcı fonksiyonlara geçilen ssh seçenekleri (port dahil)
    def SSH_P = "-p ${sshPort} ${SSH_OPTS}"

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

        // basename için ayri bir `sh` açmaya gerek yok — Groovy tarafında hesaplanır.
        // (Sandbox'ta new File yasak olduğu için string işlemi kullanılıyor.)
        if (!destPath) {
            def baseName = src.replaceAll(/\/+$/, '').tokenize('/').last()
            destPath = "/app/${appName}/${baseName}"
        }

        // Varlık kontrolü Jenkins yerleşiği ile — kabuk açılmıyor.
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

        // Dizin mi dosya mi: bunun için Jenkins yerleşiği yok, tek `sh` kalıyor.
        def isDir = false
        withEnv(["DS_SRC=${src}"]) {
            isDir = (sh(script: 'test -d "$DS_SRC"', returnStatus: true) == 0)
        }
        def srcType = isDir ? "dizin" : "dosya"
        echo "📦 Kaynak türü: ${srcType} (${src})"

        // Sunucudaki hedefin durumu TEK ssh ile öğrenilir (önceden iki ayri bağlantı açılıyordu):
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

            // Tür uyuşmazlığında hedef temizlenir. Kaynak DİZİN ise hedef de dizin olsa bile
            // temizlenir: `scp -r src host:dest` hedef dizin zaten varsa dest/src/ olarak iç içe
            // kopyalıyor; dosyalar güncellenmiyor, her deploy'da bir kat daha birikiyordu.
            if (isDir || isDir != remoteIsDir) {
                // rm -rf YIKICI ve hedefi services.yml'den (extra_files[].dest) geliyor.
                // Guardrail: `dest: /app/<appName>` ya da çok sığ bir yol uygulama kökünü
                // (docker-compose.yml ve .env dahil) ya da sistem dizinini silerdi.
                def rmTarget = destPath.toString().trim().replaceAll(/\/+$/, '')
                def segmentSayisi = rmTarget.split('/').findAll { it }.size()
                def appRoot = "/app/${appName}".toString()
                if (!rmTarget.startsWith('/') || segmentSayisi < 2 || rmTarget == appRoot) {
                    error """⛔ extra_files 'dest' değeri rm -rf için fazla geniş: '${destPath}'
  Bu yol silinirse uygulama kökünü ya da sistem dizinini götürür.
  En az iki seviye derinlikte, spesifik bir hedef verin (örnek: ${appRoot}/nginx.conf)."""
                }
                echo "⚠️ Sunucuda ${destPath} ${remoteType} olarak mevcut, ${srcType} kopyalamak için temizleniyor..."
                withEnv([
                    "DS_SSH_TARGET=${sshUser}@${deployIp}",
                    "DS_SSH_PORT=${sshPort}",
                    "DS_SSH_OPTS=${SSH_OPTS}",
                    "DS_DEST=${rmTarget}"
                ]) {
                    sh 'ssh -p "$DS_SSH_PORT" $DS_SSH_OPTS "$DS_SSH_TARGET" "rm -rf \'$DS_DEST\'"'
                }
            }
        }

        // Hedef path'i temizle (sonundaki / karakterlerini kaldır)
        def cleanDestPath = destPath.toString().replaceAll(/\/+$/, '')

        // Kaynağın türüne göre scp. Yollar tirnak icinde: bosluk iceren dizin adları
        // önceden komutu bölüp yanlış dosya kopyalanmasina yol açıyordu.
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
            // Değerler withEnv ile KAPSAMLI olarak geçirilir; global env'e yazılmaz.
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
                    
                    // Değer ÇİFT TIRNAK içinde yazılır. docker compose (compose-go dotenv) tırnaksız değerde
                    // hiçbir kaçışı çözmüyor: \" \\ \n olduğu gibi kalıyor, yani içinde " \ $ ya da satır sonu
                    // geçen şifre container'a bozuk gidiyordu. Çift tırnakta \\ \" \$ \n çözülüyor.
                    // Backtick kaçışlanmaz: compose \` dizisini tanımıyor, ters bölü değere karışıyordu.
                    def escapedValue = valueStr
                        .replace('\\', '\\\\')  // \ önce escape edilmeli
                        .replace('$', '\\$')
                        .replace('"', '\\"')
                        .replace('\n', '\\n')
                        .replace('\r', '')
                    
                    // Key'i regex için escape et
                    def escapedKey = keyStr.replaceAll(/[.+*?^$()\[\]{}|\\]/, '\\\\$0')
                    
                    // Değişken zaten varsa güncelle, yoksa ekle (satır başından kontrol et)
                    if ((envFileContent =~ /(?m)^${escapedKey}=/).find()) {
                        // quoteReplacement şart: aksi halde escapedValue içindeki ters bölü ve dolar
                        // replaceAll tarafından yorumlanıyor, ekle-dalı ile farklı içerik üretiyordu.
                        envFileContent = envFileContent.replaceAll(/(?m)^${escapedKey}=.*$/, java.util.regex.Matcher.quoteReplacement("${keyStr}=\"${escapedValue}\""))
                        echo "  ✅ ${keyStr} güncellendi"
                    } else {
                        envFileContent += "${keyStr}=\"${escapedValue}\"\n"
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
        // "down" kaldırıldı (tüm container'ları önce durdurmak gereksiz kesinti yaratıyordu) ama düz
        // "up -d" yalnızca config/image'i değişen container'ı yeniler: değişmeyen nginx eski
        // container'da kalıyor, upstream IP'lerini başlangıçta çözdüğü için yeni backend
        // container'larına ulaşamıyor → 502. --force-recreate "down"ın tazeliğini geri getirir ama
        // bağımlılık sırasına göre servis servis yeniler, her şeyi önceden durdurmaz.
        // Sabit "sleep" yerine "--wait": container'lar (healthcheck varsa healthy, yoksa running)
        // olana kadar beklenir; olmazsa komut hata döner ve Jenkins adımı durur.
        def waitArgs = composeWait ? resolveComposeWaitArgs(sshUser, sshPort, deployIp, composeWaitTimeout) : ""
        if (composeWait && !waitArgs) {
            echo "  ⚠️  --wait desteklenmiyor → `up -d` sonrası sağlık beklenmeyecek"
        }
        def composeBase = "cd /app/${appName} && docker compose -f ${dockerComposeRemoteFile}${shouldCreateEnv ? ' --env-file .env' : ''}"
        sh """
            ssh ${SSH_P} ${sshUser}@${deployIp} "sudo chown -R ${sshUser}:${sshUser} /app/${appName}/"
        """
        bindMountIzinleriniAyarla(SSH_P, "${sshUser}@${deployIp}", appName, dockerComposeRemoteFile,
                                  deployedImagesByHost[hostKey])
        def upStartMs = System.currentTimeMillis()
        def upStatus = sh(
            script: "ssh ${SSH_P} ${sshUser}@${deployIp} \"${composeBase} up -d${forceRecreate ? ' --force-recreate' : ''}${waitArgs}\"",
            returnStatus: true
        )
        if (upStatus != 0 && waitArgs) {
            echo "  ⚠️ compose up --wait hata döndü (genelde erken 'unhealthy'); ${composeWaitTimeout}s dolana kadar durum tekrar yoklanıyor..."
            if (waitUntilHealthy(SSH_P, "${sshUser}@${deployIp}", composeBase, upStartMs + composeWaitTimeout * 1000L)) {
                upStatus = 0
            }
        }
        // Loglar her durumda basılıyor: up fail olunca container'ın neden hazır olmadığı görünsün.
        sh """
            ssh ${SSH_P} ${sshUser}@${deployIp} "${composeBase} ps -a; ${composeBase} logs --tail=100" || true
        """
        if (upStatus != 0) {
            error "❌ [${serviceName}] ${deployIp} üzerinde docker compose up başarısız (container'lar ${composeWaitTimeout}s içinde hazır olmadı ya da başlamadı)"
        }

        // Aynı tag (ör. test-v1.0) tekrar deploy edildiğinde eski container yeni imaja geçer ve eski
        // imaj hiçbir tag'e/container'a bağlı kalmaz (dangling) → burada temizlenir. "-a" YOK: başka
        // bir tag'e bağlı imajlar (elle saklanan eski version'lar) hiç etkilenmez.
        if (pruneDangling) {
            sh """
                ssh ${SSH_P} ${sshUser}@${deployIp} "docker image prune -f || true"
            """
        }

        // pruneImages: varsayılan AÇIK. Bu build'de bu host'a deploy edilen imajların ESKİ sürüm
        // tag'leri silinir (test-v1.1 deploy olduysa test-v1.0 gider). Önceden burada
        // `docker image prune -a -f` vardı; host'taki boştaki HER imajı (komşu uygulamalar, base
        // imajlar, araç imajları) siliyordu.
        if (pruneImages) {
            eskiSurumTagleriniSil(SSH_P, "${sshUser}@${deployIp}", deployedImagesByHost[hostKey])
        }
    } else if (!runCompose) {
        // Bilerek erteleniyor: compose up host başına bir kez, o host'a deploy edilen SON servisle
        // çalışır ve compose dosyasındaki tüm container'ları (bu servis dahil) ayağa kaldırır.
        echo "⏭️ [${serviceName}] compose up ertelendi → bu host'taki son servisle birlikte tek seferde çalışacak"
    } else {
        echo "⏭️ [${serviceName}] compose up bu host'ta zaten çalıştı (composeOnceKey=${composeOnceKey}) → tekrar atlandı"
    }
    
    echo "✅ [${serviceName}] Deploy tamamlandı"
}

