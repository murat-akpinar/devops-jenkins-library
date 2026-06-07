// Checkov quality gate: başarısız check sayısına göre eşik
// checkovQualityGate(env.APP)        → varsayılan: 0 kritik hata toleransı
// checkovQualityGate(env.APP, 5)     → 5'e kadar başarısız check geçer
// checkovQualityGate(env.APP, -1)    → sadece API yanıtı loglanır, pipeline durdurmaz

def call(String projectName, int maxFailed = 0) {
    def CFG           = globalConfig()
    def dockerScanHost = CFG.DOCKERSCAN_HOST
    def sshUser       = CFG.DOCKERSCAN_SSH_USER
    def dashboardPort = CFG.DOCKERSCAN_BACKEND_PORT
    def dashboardUrl  = "http://${dockerScanHost}:${dashboardPort}"
    def retryCount    = 12
    def retryDelay    = 10

    def bar = '═' * 60
    echo """
╔${bar}╗
║  🛡️  Checkov Quality Gate Kontrol Ediliyor
╚${bar}╝
  📦 Proje        : ${projectName}
  🎯 Maks Hatalı  : ${maxFailed < 0 ? 'Sınırsız (bilgi amaçlı)' : maxFailed}
  🌐 Dashboard    : ${dashboardUrl}"""

    def t0 = System.currentTimeMillis()

    def responseText = ''
    for (int attempt = 1; attempt <= retryCount; attempt++) {
        sleep(retryDelay)
        responseText = sh(
            script: "ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} \"curl -sf 'http://localhost:${dashboardPort}/api/checkov?project=${projectName}'\" || true",
            returnStdout: true
        ).trim()
        if (responseText) {
            echo "  ┌─ [${attempt}/${retryCount}] API yanıtı alındı"
            echo "  └─ ✅ Dashboard yanıt verdi"
            break
        }
        echo "  ⏳ (${attempt}/${retryCount}) API henüz hazır değil, bekleniyor..."
    }

    if (!responseText) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  Checkov Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata : Dashboard ${retryCount * retryDelay}s içinde yanıt vermedi"""
        error "❌ Checkov Dashboard API ${retryCount * retryDelay}s içinde yanıt vermedi: ${dashboardUrl}/api/checkov?project=${projectName}"
    }

    def response = readJSON text: responseText
    def project  = response.projects?.find { it.projectName == projectName }
    if (!project) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  Checkov Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata    : '${projectName}' projesi bulunamadı
  📋 Mevcut  : ${response.projects?.collect { it.projectName }}"""
        error "❌ '${projectName}' projesi Checkov Dashboard'da bulunamadı. Mevcut: ${response.projects?.collect { it.projectName }}"
    }

    def totalPassed = project.passed  ?: 0
    def totalFailed = project.failed  ?: 0
    def total       = totalPassed + totalFailed
    def passRate    = total > 0 ? ((totalPassed / total) * 100).toInteger() : 100

    echo """
  ┌─ Proje Raporu
  │  ✅ Geçen   : ${totalPassed}
  │  ❌ Başarısız: ${totalFailed}
  │  📊 Toplam  : ${total}  (Geçme oranı: %${passRate})"""
    project.images?.each { img ->
        echo "  │  ${img.imageName.padRight(30)} geçen: ${img.passed ?: 0}  başarısız: ${img.failed ?: 0}"
    }
    echo "  └─ 🎯 Eşik: maks ${maxFailed < 0 ? '∞' : maxFailed} başarısız"

    def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()

    if (maxFailed < 0 || totalFailed <= maxFailed) {
        echo """
╔${bar}╗
║  ✅  Checkov Quality Gate Geçti  (${elapsed}s)
╚${bar}╝
  📊 Başarısız : ${totalFailed}  ≤  Eşik : ${maxFailed < 0 ? '∞' : maxFailed}"""
    } else {
        echo """
╔${bar}╗
║  ❌  Checkov Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Başarısız : ${totalFailed}  >  Eşik : ${maxFailed}"""
        error "⛔ [${projectName}] Checkov Quality Gate başarısız — Başarısız check: ${totalFailed}, Eşik: ${maxFailed}"
    }
}
