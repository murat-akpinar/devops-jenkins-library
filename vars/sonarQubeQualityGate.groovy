def call() {
    echo "⏳ SonarQube Quality Gate kontrol ediliyor..."
    def result = waitForQualityGate()
    if (result.status != 'OK') {
        error "⛔ SonarQube Quality Gate başarısız: ${result.status}"
    } else {
        echo "✅ SonarQube Quality Gate geçti: ${result.status}"
    }
}

