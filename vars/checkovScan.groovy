def call(String servicePath, String imageName, String tag) {
    def CFG        = globalConfig()
    def enabled    = CFG.CHECKOV_ENABLED   != false
    def softFail   = CFG.CHECKOV_SOFT_FAIL == true
    def dockerScanHost = CFG.DOCKERSCAN_HOST
    def sshUser        = CFG.DOCKERSCAN_SSH_USER
    def scriptPath = CFG.CHECKOV_SCRIPT_PATH ?: '/app/DockScan/trigger-checkov.sh'

    if (!enabled) { echo "⏭️  Checkov devre dışı, atlanıyor"; return }

    def bar = '═' * 60
    echo """
╔${bar}╗
║  🛡️  Checkov IaC Güvenlik Taraması Başlatılıyor
╚${bar}╝
  📦 İmaj     : ${imageName}:${tag}
  🖥️  Sunucu   : ${sshUser}@${dockerScanHost}
  📂 Script   : ${scriptPath}
  ⚙️  Soft Fail : ${softFail ? 'Evet (hata olsa pipeline devam eder)' : "Hayır (hata pipeline'ı durdurur)"}"""

    def t0 = System.currentTimeMillis()
    try {
        sh """
            { set +x; } 2>/dev/null; printf '  ┌─[1/3] Uzak dizin hazırlanıyor...\\n'
            { set -x; } 2>/dev/null
            ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} 'rm -rf /tmp/checkov-input/${imageName} && mkdir -p /tmp/checkov-input/${imageName}'
            { set +x; } 2>/dev/null; printf '  └─ ✅ Hazır → /tmp/checkov-input/${imageName}\\n\\n  ┌─[2/3] Kaynak dosyalar yükleniyor...\\n  │  ${servicePath}/ ──▶ ${sshUser}@${dockerScanHost}:/tmp/checkov-input/${imageName}/\\n'
            { set -x; } 2>/dev/null
            scp -r -o StrictHostKeyChecking=no \${WORKSPACE}/${servicePath}/. ${sshUser}@${dockerScanHost}:/tmp/checkov-input/${imageName}/
            { set +x; } 2>/dev/null; printf '  └─ ✅ Yükleme tamamlandı\\n\\n  ┌─[3/3] IaC güvenlik taraması çalıştırılıyor...\\n'
            { set -x; } 2>/dev/null
            ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} '${scriptPath} --image ${imageName} --tag ${tag}'
            { set +x; } 2>/dev/null; printf '  └─ ✅ Tarama tamamlandı\\n'
        """

        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ✅  Checkov Taraması Başarılı  (${elapsed}s)
╚${bar}╝"""
    } catch (e) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  Checkov Taraması Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata    : ${e.message}
  📦 İmaj    : ${imageName}:${tag}
  🖥️  Sunucu  : ${sshUser}@${dockerScanHost}"""
        if (softFail) {
            echo "  ⚠️  Soft Fail aktif → pipeline devam ediyor"
        } else {
            error "❌ [Checkov] ${imageName}:${tag} tarama başarısız: ${e.message}"
        }
    }
}
