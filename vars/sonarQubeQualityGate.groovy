def call(Map config = [:]) {
    def bar = '═' * 60
    // Webhook hiç gelmezse adım süresiz asılı kalırdı; yalnızca job seviyesindeki timeout
    // kurtarırdı (örnek Jenkinsfile'da 19999 sn ~ 5,5 saat) ve o süre boyunca bir executor
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
