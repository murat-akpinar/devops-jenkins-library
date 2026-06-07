def call(String tag) {
    def CFG         = globalConfig()
    def enabled     = CFG.OSV_ENABLED   != false
    def softFail    = CFG.OSV_SOFT_FAIL == true
    def dockerScanHost  = CFG.DOCKERSCAN_HOST
    def sshUser     = CFG.DOCKERSCAN_SSH_USER
    def scriptPath  = CFG.OSV_SCRIPT_PATH ?: '/app/DockerScan/trigger-osv.sh'

    if (!enabled) { echo "⏭️  OSV-Scanner devre dışı, atlanıyor"; return }

    def bar = '═' * 60
    def servicesConfig = readYaml file: 'services.yml'

    echo """
╔${bar}╗
║  🔎  OSV-Scanner Bağımlılık Taraması Başlatılıyor
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
                { set +x; } 2>/dev/null; printf '  │  [1/4] Uzak dizin hazırlanıyor...\\n'
                { set -x; } 2>/dev/null
                ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} 'rm -rf /tmp/osv-input/${imageName} && mkdir -p /tmp/osv-input/${imageName}'
                { set +x; } 2>/dev/null; printf '  │  [2/4] Kaynak dosyalar yükleniyor...\\n  │  ${servicePath}/ ──▶ ${sshUser}@${dockerScanHost}:/tmp/osv-input/${imageName}/\\n'
                { set -x; } 2>/dev/null
                scp -r -o StrictHostKeyChecking=no \${WORKSPACE}/${servicePath}/. ${sshUser}@${dockerScanHost}:/tmp/osv-input/${imageName}/
                { set +x; } 2>/dev/null; printf '  │  [3/4] JSON encoding düzeltiliyor (BOM temizleniyor)...\\n'
                { set -x; } 2>/dev/null
                ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} "find /tmp/osv-input/${imageName} -name '*.json' -print0 | xargs -r -0 sed -i '1s/^\\xef\\xbb\\xbf//'"
                { set +x; } 2>/dev/null; printf '  │  [4/4] Bağımlılık taraması çalıştırılıyor...\\n'
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
║  ❌  OSV-Scanner Başarısız  (${elapsed}s)
╚${bar}╝"""
        error "❌ [OSV-Scanner] Bir veya daha fazla servis taraması başarısız oldu."
    } else {
        echo """
╔${bar}╗
║  ✅  OSV-Scanner Tamamlandı  (${elapsed}s)
╚${bar}╝"""
    }

}
