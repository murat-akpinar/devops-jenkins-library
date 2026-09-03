// Not skalası (iyiden kötüye): A → B → C → D → F
// checkovQualityGate(env.APP, 'C')  →  A,B,C geçer; D,F durdurur
// checkovQualityGate(env.APP, 'D')  →  A,B,C,D geçer; sadece F durdurur
//
// Harf notu başarısız check sayısına göre hesaplanır (servis bazında, en kötüsü alınır):
//   A: 0  |  B: ≤2  |  C: ≤5  |  D: ≤10  |  F: >10

def call(String projectName, String grade = 'C') {
    def CFG            = globalConfig()
    def dockerScanHost = CFG.DOCKERSCAN_HOST
    def sshUser        = CFG.DOCKERSCAN_SSH_USER
    def dashboardPort  = CFG.DOCKERSCAN_BACKEND_PORT
    def dashboardUrl   = "http://${dockerScanHost}:${dashboardPort}"
    def gradeOrder     = [A: 1, B: 2, C: 3, D: 4, F: 5]
    def retryCount     = 12
    def retryDelay     = 10

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
        // Önce sorgula, sonra bekle. Önceki sürümde sleep döngünün basindaydi ve sonuc
        // hazir olsa bile her gate koşulsuz olarak retryDelay kadar bekliyordu.
        if (attempt > 1) { sleep(retryDelay) }
        responseText = sh(
            script: "ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} \"curl -sf 'http://localhost:${dashboardPort}/api/checkov?project=${projectName}'\" || true",
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
║  ❌  Checkov Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata : '${projectName}' için tarama kaydı bulunamadı"""
        error "❌ '${projectName}' projesi için Checkov tarama kaydı bulunamadı. Tarama başarıyla tamamlandı mı?"
    }

    // /api/checkov flat array döner: [{projectName, serviceName, tag, passed, failed, ...}, ...]
    def allScans = readJSON text: responseText
    def scans = allScans

        // API ?project=X ile sorgulanıyor ve dönen dizi tüm taramaları içerebiliyor.
        // Filtrelenmezse bir kez kötü not almış ESKİ bir tag sonraki tüm build'leri
        // sınırsız süre fail ettirir. Bu build'in tag'ine ait kayıt varsa yalnızca onlar
        // değerlendirilir; yoksa (API zaten yalnızca son taramayi döndürüyorsa) eskisi
        // gibi tamamı kullanılır — iki API davranışında da doğru çalışır.
        def currentTag = env.VERSION?.trim()
        if (currentTag) {
            def matching = allScans.findAll { it?.tag == currentTag }
            if (matching) {
                scans = matching
                echo "  🏷️  Tag filtresi: ${currentTag} (${matching.size()}/${allScans.size()} kayıt)"
            } else {
                echo "  ⚠️  '${currentTag}' tag'ine ait kayıt yok → tüm kayıtlar değerlendiriliyor"
            }
        }

    def totalPassed = 0
    def totalFailed = 0
    def worstGrade  = 'A'


    // Dashboard aynı (servis, tag) için birden fazla kayıt tutabiliyor: her yeni koşu bir
    // kayıt daha ekliyor. Hepsi değerlendirilirse ESKİ bir koşunun sonucu da nota giriyor
    // (örneğin taramanın patladığı bir kosudan kalan "0 bulgu" kaydı). Servis başına
    // yalnızca SON kayıt alınır; kayıtlar zaman damgasi taşıyorsa ona göre, taşımıyorsa
    // dizideki son görünüme göre.
    def latestByService = [:]
    scans.each { sc ->
        def svcKey = (sc?.serviceName ?: '?').toString()
        def prev = latestByService[svcKey]
        if (prev == null) {
            latestByService[svcKey] = sc
        } else {
            def a = (sc?.timestamp ?: sc?.scannedAt ?: sc?.date ?: '').toString()
            def b = (prev?.timestamp ?: prev?.scannedAt ?: prev?.date ?: '').toString()
            latestByService[svcKey] = (a && b) ? ((a >= b) ? sc : prev) : sc
        }
    }
    if (latestByService.size() < scans.size()) {
        echo "  ♻️  Mükerrer kayıt: ${scans.size()} → ${latestByService.size()} (servis başına son tarama)"
    }
    scans = latestByService.values() as List

    echo "  ┌─ Servis Raporları"
    scans.each { scan ->
        def svcFailed = scan.failed ?: 0
        def svcPassed = scan.passed ?: 0
        def svcGrade  = computeCheckovGrade(svcFailed)
        totalFailed += svcFailed
        totalPassed += svcPassed
        if (gradeOrder[svcGrade] > gradeOrder[worstGrade]) {
            worstGrade = svcGrade
        }
        echo "  │  ${(scan.serviceName ?: '?').padRight(20)} [${scan.tag}]  geçen: ${svcPassed}  başarısız: ${svcFailed}  → ${svcGrade}"
    }

    def total = totalPassed + totalFailed
    echo """  └─ Özet
     📊 Proje Notu : ${worstGrade}
     ✅ Geçen      : ${totalPassed}
     ❌ Başarısız  : ${totalFailed}
     📋 Toplam     : ${total}
     🎯 Eşik Notu  : ${grade}"""

    def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
    if (gradeOrder[worstGrade] <= gradeOrder[grade]) {
        echo """
╔${bar}╗
║  ✅  Checkov Quality Gate Geçti  (${elapsed}s)
╚${bar}╝
  📊 Proje Notu : ${worstGrade}  ≤  Eşik : ${grade}"""
    } else {
        echo """
╔${bar}╗
║  ❌  Checkov Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Proje Notu : ${worstGrade}  >  Eşik : ${grade}"""
        error "⛔ [${projectName}] Checkov Quality Gate başarısız — Proje notu: ${worstGrade}, Eşik: ${grade}"
    }
}

// failed sayısına göre harf notu (frontend/backend ile aynı eşikler)
def computeCheckovGrade(int failed) {
    if (failed == 0)   return 'A'
    if (failed <= 2)   return 'B'
    if (failed <= 5)   return 'C'
    if (failed <= 10)  return 'D'
    return 'F'
}
