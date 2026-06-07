def call() {
    def bar = '═' * 60
    echo """
╔${bar}╗
║  🎯  SonarQube Quality Gate Kontrol Ediliyor
╚${bar}╝
  ⏳ Sonuç bekleniyor..."""

    def t0 = System.currentTimeMillis()
    def result = waitForQualityGate()
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
