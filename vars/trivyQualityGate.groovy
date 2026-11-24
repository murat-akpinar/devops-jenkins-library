// Not skalası (iyiden kötüye): A → B → C → D → F
// trivyQualityGate(env.APP, 'C')  →  A,B,C geçer; D,F durdurur
// trivyQualityGate(env.APP, 'D')  →  A,B,C,D geçer; sadece F durdurur

def call(String projectName, String grade = 'C') {
    // error() mesajlarında PROJE ADI bilerek yok. Jenkins bağlı bir credential'ın DEĞERİNİ log'da
    // nerede görürse maskeliyor; proje adı bir credential değeriyle çakışırsa (ör. RABBITMQ_USER
    // proje adıyla aynı) ad [****] oluyor, error() AbortException yerine MaskedException fırlatıp
    // mesaj yerine uzun bir Java stack trace basıyor. Ad zaten yukarıdaki kutularda echo ediliyor.
    def CFG          = globalConfig()
    def dockerScanHost    = CFG.DOCKERSCAN_HOST
    def sshUser      = CFG.DOCKERSCAN_SSH_USER
    def dashboardPort = CFG.DOCKERSCAN_BACKEND_PORT
    def dashboardUrl = "http://${dockerScanHost}:${dashboardPort}"
    def gradeOrder   = [A: 1, B: 2, C: 3, D: 4, F: 5]
    def retryCount   = 12
    def retryDelay   = 10

    grade = grade.toUpperCase()
    if (!gradeOrder.containsKey(grade)) {
        error "❌ Geçersiz grade: '${grade}'. Geçerli değerler: A, B, C, D, F"
    }

    def bar = '═' * 60
    echo """
╔${bar}╗
║  🎯  Trivy Quality Gate Kontrol Ediliyor
╚${bar}╝
  📦 Proje     : ${projectName}
  🏅 Eşik      : ${grade}
  🌐 Dashboard : ${dashboardUrl}"""

    def t0 = System.currentTimeMillis()

    def responseText = ''
    for (int attempt = 1; attempt <= retryCount; attempt++) {
        // Önce sorgula, sonra bekle. Önceki sürümde sleep döngünün basindaydi ve sonuc
        // hazir olsa bile her gate koşulsuz olarak retryDelay kadar bekliyordu.
        if (attempt > 1) { sleep(retryDelay) }
        responseText = sh(
            script: "ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} \"curl -sf 'http://localhost:${dashboardPort}/api/grades?project=${projectName}'\" || true",
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
║  ❌  Trivy Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata : Dashboard ${(retryCount - 1) * retryDelay}s içinde yanıt vermedi"""
        error "❌ Trivy Dashboard API ${(retryCount - 1) * retryDelay}s içinde yanıt vermedi: ${dashboardUrl}/api/grades"
    }

    def response = readJSON text: responseText
    def project  = response.projects?.find { it?.projectName == projectName }
    if (!project) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  Trivy Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata    : '${projectName}' projesi bulunamadı
  📋 Mevcut  : ${response.projects?.collect { it.projectName }}"""
        error "❌ Proje Trivy Dashboard'da bulunamadı — aranan ad ve mevcut projeler yukarıdaki kutuda."
    }

    def projectGrade = project.grade?.toUpperCase()
    echo """
  ┌─ Proje Raporu
  │  📊 Not   : ${projectGrade}  (${project.imageCount} imaj)"""
    project.images?.each { img ->
        echo "  │  ${img.imageName.padRight(30)} ${img.grade}  (zafiyet: ${img.totalVulns ?: 0})"
    }
    echo "  └─ 🎯 Eşik Notu: ${grade}"

    if (!gradeOrder.containsKey(projectGrade)) {
        error "❌ Bilinmeyen not: '${projectGrade}'"
    }

    def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
    if (gradeOrder[projectGrade] <= gradeOrder[grade]) {
        echo """
╔${bar}╗
║  ✅  Trivy Quality Gate Geçti  (${elapsed}s)
╚${bar}╝
  📊 Proje Notu : ${projectGrade}  ≤  Eşik : ${grade}"""
    } else {
        echo """
╔${bar}╗
║  ❌  Trivy Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Proje Notu : ${projectGrade}  >  Eşik : ${grade}"""
        error "⛔ Trivy Quality Gate başarısız — Proje notu: ${projectGrade}, Eşik: ${grade}"
    }
}
