// Not skalası (iyiden kötüye): A → B → C → D → F
// trivyQualityGate(env.APP, 'C')  →  A,B,C geçer; D,F durdurur
// trivyQualityGate(env.APP, 'D')  →  A,B,C,D geçer; sadece F durdurur

def call(String projectName, String grade = 'C') {
    def CFG          = globalConfig()
    def dockerScanHost     = CFG.DOCKERSCAN_HOST
    def sshUser       = CFG.DOCKERSCAN_SSH_USER
    def dashboardPort = CFG.DOCKERSCAN_BACKEND_PORT
    def dashboardUrl  = "http://${dockerScanHost}:${dashboardPort}"
    def gradeOrder   = [A: 1, B: 2, C: 3, D: 4, F: 5]
    def retryCount   = 12   // toplam deneme
    def retryDelay   = 10   // saniye arayla → max ~2 dakika bekler

    grade = grade.toUpperCase()
    if (!gradeOrder.containsKey(grade)) {
        error "❌ Geçersiz grade: '${grade}'. Geçerli değerler: A, B, C, D, F"
    }

    echo "⏳ [${projectName}] DockerScan Quality Gate kontrol ediliyor (Eşik: ${grade})..."

    def responseText = ''
    for (int attempt = 1; attempt <= retryCount; attempt++) {
        sleep(retryDelay)
        responseText = sh(
            script: "ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} \"curl -sf 'http://localhost:${dashboardPort}/api/grades?project=${projectName}'\" || true",
            returnStdout: true
        ).trim()
        if (responseText) {
            echo "  ✅ API yanıtı alındı (deneme ${attempt}/${retryCount})"
            break
        }
        echo "  ⏳ API henüz hazır değil, bekleniyor... (${attempt}/${retryCount})"
    }

    if (!responseText) {
        error "❌ DockerScan Dashboard API ${retryCount * retryDelay}s içinde yanıt vermedi: ${dashboardUrl}/api/grades?project=${projectName}"
    }
    def response     = readJSON text: responseText
    def project      = response.projects?.find { it.projectName == projectName }
    if (!project) {
        error "❌ '${projectName}' projesi DockerScan Dashboard'da bulunamadı. Mevcut: ${response.projects?.collect { it.projectName }}"
    }
    def projectGrade = project.grade?.toUpperCase()
    echo "📊 Proje: ${projectName}  →  Not: ${projectGrade}  (${project.imageCount} imaj)"
    project.images?.each { img ->
        echo "  ${img.imageName.padRight(30)} ${img.grade}  (toplam zafiyet: ${img.totalVulns ?: 0})"
    }
    echo "🎯 Eşik Notu: ${grade}"
    if (!gradeOrder.containsKey(projectGrade)) {
        error "❌ Bilinmeyen not: '${projectGrade}'"
    }
    if (gradeOrder[projectGrade] <= gradeOrder[grade]) {
        echo "✅ [${projectName}] DockerScan Quality Gate geçti (${projectGrade} ≤ ${grade})"
    } else {
        error "⛔ [${projectName}] DockerScan Quality Gate başarısız — Proje notu: ${projectGrade}, Eşik: ${grade}"
    }
}
