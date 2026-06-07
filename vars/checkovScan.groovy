def call(String tag) {
    def CFG         = globalConfig()
    def enabled     = CFG.CHECKOV_ENABLED   != false
    def softFail    = CFG.CHECKOV_SOFT_FAIL == true
    def dockerScanHost  = CFG.DOCKERSCAN_HOST
    def sshUser     = CFG.DOCKERSCAN_SSH_USER
    def scriptPath  = CFG.CHECKOV_SCRIPT_PATH ?: '/app/DockerScan/trigger-checkov.sh'

    if (!enabled) { echo "⏭️  Checkov devre dışı, atlanıyor"; return }

    def bar = '═' * 60
    def servicesConfig = readYaml file: 'services.yml'

    echo """
╔${bar}╗
║  🛡️  Checkov IaC Güvenlik Taraması Başlatılıyor
╚${bar}╝
  🏷️  Tag      : ${tag}
  🖥️  Sunucu   : ${sshUser}@${dockerScanHost}
  📂 Script   : ${scriptPath}
  ⚙️  Soft Fail : ${softFail ? 'Evet (hata olsa pipeline devam eder)' : "Hayır (hata pipeline'ı durdurur)"}"""

    def t0 = System.currentTimeMillis()
    def hasError = false

    servicesConfig.services.each { svc ->
        def key         = svc.name?.trim()?.toUpperCase()
        def imageName   = svc.image_name ?: svc.name
        def servicePath = svc.path?.replaceAll('/+$', '') ?: svc.name

        if (env."DO_${key}" != '1') {
            echo "  ⏭️  [${svc.name}]: build edilmedi → tarama atlanıyor."
            return
        }

        echo "  ┌─ [${imageName}:${tag}] taranıyor..."
        try {
            sh """
                { set +x; } 2>/dev/null; printf '  │  [1/3] Uzak dizin hazırlanıyor...\\n'
                { set -x; } 2>/dev/null
                ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} 'rm -rf /tmp/checkov-input/${imageName} && mkdir -p /tmp/checkov-input/${imageName}'
                { set +x; } 2>/dev/null; printf '  │  [2/3] Kaynak dosyalar yükleniyor...\\n  │  ${servicePath}/ ──▶ ${sshUser}@${dockerScanHost}:/tmp/checkov-input/${imageName}/\\n'
                { set -x; } 2>/dev/null
                scp -r -o StrictHostKeyChecking=no \${WORKSPACE}/${servicePath}/. ${sshUser}@${dockerScanHost}:/tmp/checkov-input/${imageName}/
                { set +x; } 2>/dev/null; printf '  │  [3/3] IaC güvenlik taraması çalıştırılıyor...\\n'
                { set -x; } 2>/dev/null
                ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} '${scriptPath} --image ${imageName} --tag ${tag}'
            """
            echo "  └─ ✅ [${imageName}:${tag}] tamamlandı"
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
