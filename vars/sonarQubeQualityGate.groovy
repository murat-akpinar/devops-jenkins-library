def call(Map config = [:]) {
    def bar = '═' * 60
    // Webhook hic gelmezse adim suresiz asili kalirdi; yalnizca job seviyesindeki timeout
    // kurtarirdi (ornek Jenkinsfile'da 19999 sn ~ 5,5 saat) ve o sure boyunca bir executor
    // tutuluyordu.
    def timeoutMinutes = config.timeoutMinutes ?: 10
    echo """
╔${bar}╗
║  🎯  SonarQube Quality Gate Kontrol Ediliyor
╚${bar}╝
  ⏳ Sonuç bekleniyor..."""

    def t0 = System.currentTimeMillis()
    def result = null
    timeout(time: timeoutMinutes, unit: 'MINUTES') {
        result = waitForQualityGate()
    }
    def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()

    if (result.status != 'OK') {
        echo """
╔${bar}╗
║  ❌  SonarQube Quality Gate Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Durum : ${result.status}"""
        error "⛔ SonarQube Quality Gate başarısız: ${result.status}"
    } else {
        echo """
╔${bar}╗
║  ✅  SonarQube Quality Gate Geçti  (${elapsed}s)
╚${bar}╝
  🟢 Durum : ${result.status}"""
    }
}
