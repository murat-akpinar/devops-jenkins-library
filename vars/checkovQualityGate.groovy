// Not skalası (iyiden kötüye): A → B → C → D → F
// checkovQualityGate(env.APP, 'C')  →  A,B,C geçer; D,F durdurur
// checkovQualityGate(env.APP, 'D')  →  A,B,C,D geçer; sadece F durdurur
//
// Harf notu pass rate'ten hesaplanır:
//   A: %100  |  B: ≥%90  |  C: ≥%75  |  D: ≥%50  |  F: <%50

def call(String projectName, String grade = 'C') {
    def CFG           = globalConfig()
    def dockerScanHost = CFG.DOCKERSCAN_HOST
    def sshUser       = CFG.DOCKERSCAN_SSH_USER
    def dashboardPort = CFG.DOCKERSCAN_BACKEND_PORT
    def dashboardUrl  = "http://${dockerScanHost}:${dashboardPort}"
    def gradeOrder    = [A: 1, B: 2, C: 3, D: 4, F: 5]
    def retryCount    = 12
    def retryDelay    = 10

    grade = grade.toUpperCase()
    if (!gradeOrder.containsKey(grade)) {
        error "❌ Geçersiz grade: '${grade}'. Geçerli değerler: A, B, C, D, F"
    }

    def bar = '═' * 60
    echo """
╔${bar}╗
║  🛡️  Checkov Quality Gate Kontrol Ediliyor
╚${bar}╝
  📦 Proje     : ${projectName}
  🏅 Eşik      : ${grade}
  🌐 Dashboard : ${dashboardUrl}"""

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
    def project  = response.projects?.find { it?.projectName == projectName }
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

    def totalPassed = project.passed ?: 0
    def totalFailed = project.failed ?: 0
    def total       = totalPassed + totalFailed
    def passRate    = total > 0 ? ((totalPassed / total) * 100).toInteger() : 100

    def projectGrade
    if      (passRate == 100) projectGrade = 'A'
    else if (passRate >= 90)  projectGrade = 'B'
    else if (passRate >= 75)  projectGrade = 'C'
    else if (passRate >= 50)  projectGrade = 'D'
    else                      projectGrade = 'F'

    echo """
  ┌─ Proje Raporu
  │  📊 Not      : ${projectGrade}  (geçme oranı: %${passRate})
  │  ✅ Geçen    : ${totalPassed}
  │  ❌ Başarısız: ${totalFailed}
  │  📋 Toplam   : ${total}"""
    project.images?.each { img ->
        echo "  │  ${img.imageName.padRight(30)} geçen: ${img.passed ?: 0}  başarısız: ${img.failed ?: 0}"
    }
    echo "  └─ 🎯 Eşik Notu: ${grade}"

    def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
    if (gradeOrder[projectGrade] <= gradeOrder[grade]) {
        echo """
╔${bar}╗
║  ✅  Checkov Quality Gate Geçti  (${elapsed}s)
╚${bar}╝
  📊 Proje Notu : ${projectGrade}  ≤  Eşik : ${grade}"""
    } else {
        echo """
╔${bar}╗
║  ❌  Checkov Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Proje Notu : ${projectGrade}  >  Eşik : ${grade}"""
        error "⛔ [${projectName}] Checkov Quality Gate başarısız — Proje notu: ${projectGrade}, Eşik: ${grade}"
    }
}
