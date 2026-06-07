// Not skalası (iyiden kötüye): A → B → C → D → F
// osvQualityGate(env.APP, 'C')  →  A,B,C geçer; D,F durdurur
// osvQualityGate(env.APP, 'D')  →  A,B,C,D geçer; sadece F durdurur

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
║  🔎  OSV-Scanner Quality Gate Kontrol Ediliyor
╚${bar}╝
  📦 Proje     : ${projectName}
  🏅 Eşik      : ${grade}
  🌐 Dashboard : ${dashboardUrl}"""

    def t0 = System.currentTimeMillis()

    def responseText = ''
    for (int attempt = 1; attempt <= retryCount; attempt++) {
        sleep(retryDelay)
        responseText = sh(
            script: "ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} \"curl -sf 'http://localhost:${dashboardPort}/api/osv?project=${projectName}'\" || true",
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
║  ❌  OSV Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata : Dashboard ${retryCount * retryDelay}s içinde yanıt vermedi"""
        error "❌ OSV Dashboard API ${retryCount * retryDelay}s içinde yanıt vermedi: ${dashboardUrl}/api/osv?project=${projectName}"
    }

    def response = readJSON text: responseText
    def project  = response.projects?.find { it?.projectName == projectName }
    if (!project) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  OSV Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata    : '${projectName}' projesi bulunamadı
  📋 Mevcut  : ${response.projects?.collect { it.projectName }}"""
        error "❌ '${projectName}' projesi OSV Dashboard'da bulunamadı. Mevcut: ${response.projects?.collect { it.projectName }}"
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
║  ✅  OSV Quality Gate Geçti  (${elapsed}s)
╚${bar}╝
  📊 Proje Notu : ${projectGrade}  ≤  Eşik : ${grade}"""
    } else {
        echo """
╔${bar}╗
║  ❌  OSV Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Proje Notu : ${projectGrade}  >  Eşik : ${grade}"""
        error "⛔ [${projectName}] OSV Quality Gate başarısız — Proje notu: ${projectGrade}, Eşik: ${grade}"
    }
}
