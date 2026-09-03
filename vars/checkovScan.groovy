def call(String tag) {
    def CFG         = globalConfig()
    def SSH_OPTS = sshOptions()
    def enabled     = CFG.CHECKOV_ENABLED   != false
    def softFail    = CFG.CHECKOV_SOFT_FAIL == true
    def dockerScanHost  = CFG.DOCKERSCAN_HOST
    def sshUser     = CFG.DOCKERSCAN_SSH_USER
    def scriptPath  = CFG.CHECKOV_SCRIPT_PATH ?: '/app/DockerScan/trigger-checkov.sh'

    if (!enabled) { echo "⏭️  Checkov devre dışı, atlanıyor"; return }

    def bar = '═' * 60
    // dockerCleanup/deployAllServices ile aynı: SERVICES_FILE env'i varsa o kullanılır
    def servicesFile = env.SERVICES_FILE?.trim() ?: 'services.yml'
    def servicesConfig = readYaml file: servicesFile
    def appName = servicesConfig.app_name ?: error("❌ services.yml içinde 'app_name' tanımlı değil!")

    echo """
╔${bar}╗
║  🛡️  Checkov IaC Güvenlik Taraması Başlatılıyor
╚${bar}╝
  🏷️  Tag      : ${tag}
  📦 Proje    : ${appName}
  🖥️  Sunucu   : ${sshUser}@${dockerScanHost}
  📂 Script   : ${scriptPath}
  ⚙️  Soft Fail : ${softFail ? 'Evet (hata olsa pipeline devam eder)' : "Hayır (hata pipeline'ı durdurur)"}"""

    def t0 = System.currentTimeMillis()
    def hasError = false

    servicesConfig.services.each { svc ->
        def key         = svc.name?.trim()?.toUpperCase()
        def imageName   = svc.image_name ?: svc.name
        // Tarama yolu build yolundan AYRI olabilir. nginx gibi servisler, Dockerfile'ları
        // repo kökünü build context'i olarak gerektirdiği için `path: '.'` kullanır; o zaman
        // tarama tüm repoyu kapsar ve diğer servislerin bağımlılıkları İKİNCİ KEZ sayılır
        // (frontend'in zafiyetleri nginx'in notuna da yansır, proje notu şişer).
        // scan_path tanımlıysa tarama yalnızca o dizini kapsar; tanımlı değilse path kullanılır.
        // Tarama yolu build yolundan AYRI olabilir ve LİSTE de olabilir.
        //
        // Ayrı olması gerekir: nginx gibi servisler, Dockerfile'ları repo kökünü build
        // context'i olarak gerektirdiği için `path: '.'` kullanır; o zaman tarama tüm repoyu
        // kapsar ve diğer servislerin bağımlılıkları İKİNCİ KEZ sayılır.
        //
        // Liste olması gerekir: compose'da kendi servisi olmayan ama kaynağı bir başka
        // servisin imajına giren dizinler (ör. nginx'in içine build edilen frontend/) aksi
        // halde hiç taranmaz. `scan_path: [nginx/, frontend/]` ikisini birden kapsar.
        def scanPaths = ((svc.scan_path ?: svc.path) instanceof Collection
                            ? (svc.scan_path ?: svc.path)
                            : [svc.scan_path ?: svc.path])
                        .collect { it?.toString()?.replaceAll('/+$', '') }
                        .findAll { it }
        if (!scanPaths) { scanPaths = [svc.name] }
        def servicePath = scanPaths.join(', ')

        if (env."DO_${key}" != '1') {
            echo "  ⏭️  [${svc.name}]: build edilmedi → tarama atlanıyor."
            return
        }

        // Tek yolda eski davranış korunur: içerik uzak dizinin köküne açılır.
        // Çoklu yolda dizinler adlarıyla taşınır (nginx/, frontend/) — tarayıcılar
        // özyinelemeli çalıştığı için bulgular aynı, yalnızca rapordaki yol değişir.
        def tarCmd = (scanPaths.size() == 1)
            ? "tar -C \${WORKSPACE}/${scanPaths[0]} --exclude='.git' --exclude='.svn' --exclude='node_modules' --exclude='vendor' --exclude='bower_components' --exclude='dist' --exclude='build' --exclude='out' --exclude='target' --exclude='bin' --exclude='obj' --exclude='__pycache__' --exclude='.venv' --exclude='venv' --exclude='.tox' --exclude='.mypy_cache' --exclude='.pytest_cache' --exclude='.terraform' --exclude='.gradle' --exclude='.idea' --exclude='.vscode' --exclude='*.env' --exclude='.env' --exclude='.env.*' -cf - ."
            : "tar -C \${WORKSPACE} --exclude='.git' --exclude='.svn' --exclude='node_modules' --exclude='vendor' --exclude='bower_components' --exclude='dist' --exclude='build' --exclude='out' --exclude='target' --exclude='bin' --exclude='obj' --exclude='__pycache__' --exclude='.venv' --exclude='venv' --exclude='.tox' --exclude='.mypy_cache' --exclude='.pytest_cache' --exclude='.terraform' --exclude='.gradle' --exclude='.idea' --exclude='.vscode' --exclude='*.env' --exclude='.env' --exclude='.env.*' -cf - ${scanPaths.join(' ')}"

        echo "  ┌─ [${imageName}:${tag}] taranıyor..."
        try {
            sh """
                { set +x; } 2>/dev/null; printf '  │  [1/3] Uzak dizin hazırlanıyor...\\n'
                { set -x; } 2>/dev/null
                ssh ${SSH_OPTS} -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} 'rm -rf /tmp/checkov-input/${imageName} && mkdir -p /tmp/checkov-input/${imageName}'
                { set +x; } 2>/dev/null; printf '  │  [2/3] Kaynak dosyalar yükleniyor...\\n  │  ${servicePath}/ ──▶ ${sshUser}@${dockerScanHost}:/tmp/checkov-input/${imageName}/\\n'
                { set -x; } 2>/dev/null
                # scp -r tüm dizini (node_modules, .git, build çıktıları ve .env dosyaları
                # dahil) tarama sunucusuna gönderiyordu; hem yavaştı hem de servis .env'i
                # uzak /tmp'de kalıyordu. tar ile filtrelenip tek akışta gönderiliyor.
                ${tarCmd} |
                  ssh ${SSH_OPTS} -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} 'tar -C /tmp/checkov-input/${imageName} -xf -'
                { set +x; } 2>/dev/null; printf '  │  [3/3] IaC güvenlik taraması çalıştırılıyor...\\n'
                { set -x; } 2>/dev/null
            """

            def rc = sh(returnStatus: true, script: """
                ssh ${SSH_OPTS} -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} '${scriptPath} --image ${imageName} --tag ${tag} --project ${appName}'
            """)

            // Uzak girdiyi her durumda temizle: kaynak kod tarama sunucusunda birikmesin.
            sh """
                { set +x; } 2>/dev/null; printf '  │  Uzak girdi temizleniyor...\\n'
                { set -x; } 2>/dev/null
                ssh ${SSH_OPTS} -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} 'rm -rf /tmp/checkov-input/${imageName}' || true
            """

            // Tetikleyici script bulgu bulunca 1 dönüyor — tarama BAŞARILI, sadece bulgu var.
            // Pass/fail kararı quality gate'in işi. 2 ve üzeri gerçek hata (script çalışmadı,
            // ssh koptu, dizin yok…): önceki kabuk sözdizimi hatası da exit 2 vermişti.
            if (rc == 0) {
                echo "  └─ ✅ [${imageName}:${tag}] tamamlandı — bulgu yok"
            } else if (rc == 1) {
                echo "  └─ ⚠️  [${imageName}:${tag}] tamamlandı — bulgu var (karar quality gate'de)"
            } else {
                error "tarama çalıştırılamadı (exit ${rc})"
            }

        } catch (e) {
            echo "  └─ ❌ [${imageName}:${tag}] başarısız: ${e.message}"
            if (!softFail) { hasError = true }
        }
    }

    def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()

    if (hasError) {
        echo """
╔${bar}╗
║  ❌  Checkov Taraması Başarısız  (${elapsed}s)
╚${bar}╝"""
        error "❌ [Checkov] Bir veya daha fazla servis taraması başarısız oldu."
    } else {
        echo """
╔${bar}╗
║  ✅  Checkov Taraması Tamamlandı  (${elapsed}s)
╚${bar}╝"""
    }

}
