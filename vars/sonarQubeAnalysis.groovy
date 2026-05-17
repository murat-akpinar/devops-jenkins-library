def call(String serverName) {
    echo "🔍 SonarQube analizi başlatılıyor..."
    withSonarQubeEnv(serverName) {
        sh 'sonar-scanner'
    }
    echo "✅ SonarQube analizi tamamlandı"
}

