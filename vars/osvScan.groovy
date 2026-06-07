def call(String servicePath, String imageName, String tag) {
    def CFG        = globalConfig()
    def enabled    = CFG.OSV_ENABLED  != false
    def softFail   = CFG.OSV_SOFT_FAIL == true
    def trivyHost  = CFG.TRIVY_HOST      ?: 'YOUR_TRIVY_HOST_IP'
    def sshUser    = CFG.TRIVY_SSH_USER  ?: 'your-user'
    def scriptPath = CFG.OSV_SCRIPT_PATH ?: '/app/DockScan/trigger-osv.sh'

    if (!enabled) { echo "⏭️  OSV-Scanner devre dışı, atlanıyor"; return }

    def bar = '═' * 60
    echo """
╔${bar}╗
║  🔎  OSV-Scanner Başlatılıyor
╚${bar}╝
  📦 İmaj     : ${imageName}:${tag}
  🖥️  Sunucu   : ${sshUser}@${trivyHost}
  📂 Script   : ${scriptPath}
  ⚙️  Soft Fail : ${softFail ? 'Evet (hata olsa pipeline devam eder)' : "Hayır (hata pipeline'ı durdurur)"}"""

    def t0 = System.currentTimeMillis()
    try {
        sh """
            { set +x; } 2>/dev/null; printf '  ┌─[1/4] Uzak dizin hazırlanıyor...\\n'
            { set -x; } 2>/dev/null
            ssh -o StrictHostKeyChecking=no ${sshUser}@${trivyHost} 'rm -rf /tmp/osv-input/${imageName} && mkdir -p /tmp/osv-input/${imageName}'
            { set +x; } 2>/dev/null; printf '  └─ ✅ Hazır → /tmp/osv-input/${imageName}\\n\\n  ┌─[2/4] Kaynak dosyalar yükleniyor...\\n  │  ${servicePath}/ ──▶ ${sshUser}@${trivyHost}:/tmp/osv-input/${imageName}/\\n'
            { set -x; } 2>/dev/null
            scp -r -o StrictHostKeyChecking=no \${WORKSPACE}/${servicePath}/. ${sshUser}@${trivyHost}:/tmp/osv-input/${imageName}/
            { set +x; } 2>/dev/null; printf '  └─ ✅ Yükleme tamamlandı\\n\\n  ┌─[3/4] JSON encoding düzeltiliyor (BOM temizleniyor)...\\n'
            { set -x; } 2>/dev/null
            ssh -o StrictHostKeyChecking=no ${sshUser}@${trivyHost} "find /tmp/osv-input/${imageName} -name '*.json' -print0 | xargs -r -0 sed -i '1s/^\\xef\\xbb\\xbf//'"
            { set +x; } 2>/dev/null; printf '  └─ ✅ Encoding düzeltildi\\n\\n  ┌─[4/4] Bağımlılık taraması çalıştırılıyor...\\n'
            { set -x; } 2>/dev/null
            ssh -o StrictHostKeyChecking=no ${sshUser}@${trivyHost} '${scriptPath} --image ${imageName} --tag ${tag}'
            { set +x; } 2>/dev/null; printf '  └─ ✅ Tarama tamamlandı\\n'
        """

        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ✅  OSV-Scanner Başarılı  (${elapsed}s)
╚${bar}╝"""
    } catch (e) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  OSV-Scanner Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata    : ${e.message}
  📦 İmaj    : ${imageName}:${tag}
  🖥️  Sunucu  : ${sshUser}@${trivyHost}"""
        if (softFail) {
            echo "  ⚠️  Soft Fail aktif → pipeline devam ediyor"
        } else {
            error "❌ [OSV-Scanner] ${imageName}:${tag} tarama başarısız: ${e.message}"
        }
    }
}
