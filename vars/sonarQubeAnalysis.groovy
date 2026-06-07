def call(String serverName) {
    def bar = '═' * 60
    echo """
╔${bar}╗
║  🔍  SonarQube Analizi Başlatılıyor
╚${bar}╝
  🖥️  Sunucu : ${serverName}"""

    def t0 = System.currentTimeMillis()
    try {
        withSonarQubeEnv(serverName) {
            sh 'sonar-scanner'
        }
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ✅  SonarQube Analizi Tamamlandı  (${elapsed}s)
╚${bar}╝"""
    } catch (e) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  SonarQube Analizi Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata   : ${e.message}
  🖥️  Sunucu : ${serverName}"""
        error "❌ [SonarQube] Analiz başarısız: ${e.message}"
    }
}
