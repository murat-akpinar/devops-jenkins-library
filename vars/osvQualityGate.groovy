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
        if (responseText && responseText != '[]') {
            echo "  ┌─ [${attempt}/${retryCount}] API yanıtı alındı"
            echo "  └─ ✅ Dashboard yanıt verdi"
            break
        }
        echo "  ⏳ (${attempt}/${retryCount}) Tarama sonuçları bekleniyor..."
    }

    if (!responseText || responseText == '[]') {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  OSV Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata : '${projectName}' için tarama kaydı bulunamadı"""
        error "❌ '${projectName}' projesi için OSV tarama kaydı bulunamadı. Tarama başarıyla tamamlandı mı?"
    }

    // /api/osv flat array döner: [{projectName, serviceName, tag, totalVulns, sources[]}, ...]
    def scans = readJSON text: responseText

    def worstGrade = 'A'

    echo "  ┌─ Servis Raporları"
    scans.each { scan ->
        def svcVulns = scan.totalVulns ?: 0
        def svcGrade = computeOsvGrade(svcVulns)
        if (gradeOrder[svcGrade] > gradeOrder[worstGrade]) {
            worstGrade = svcGrade
        }
        echo "  │  ${scan.serviceName.padRight(20)} [${scan.tag}]  zafiyet: ${svcVulns}  → ${svcGrade}"
    }

    echo """  └─ Özet
     📊 Proje Notu : ${worstGrade}
     🎯 Eşik Notu  : ${grade}"""

    def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
    if (gradeOrder[worstGrade] <= gradeOrder[grade]) {
        echo """
╔${bar}╗
║  ✅  OSV Quality Gate Geçti  (${elapsed}s)
╚${bar}╝
  📊 Proje Notu : ${worstGrade}  ≤  Eşik : ${grade}"""
    } else {
        echo """
╔${bar}╗
║  ❌  OSV Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Proje Notu : ${worstGrade}  >  Eşik : ${grade}"""
        error "⛔ [${projectName}] OSV Quality Gate başarısız — Proje notu: ${worstGrade}, Eşik: ${grade}"
    }
}

// totalVulns sayısına göre harf notu (frontend ile aynı eşikler)
def computeOsvGrade(int vulns) {
    if (vulns == 0)  return 'A'
    if (vulns <= 2)  return 'B'
    if (vulns <= 5)  return 'C'
    if (vulns <= 9)  return 'D'
    return 'F'
}
