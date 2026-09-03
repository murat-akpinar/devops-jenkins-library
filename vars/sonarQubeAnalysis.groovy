// Workspace'te .sln varsa .NET (dotnet-sonarscanner) akışı, yoksa klasik `sonar-scanner` çalıştırılır.
// Her iki durumda da proje anahtarı/adı reponun kendi sonar-project.properties dosyasından okunur;
// bu library hiçbir repo'ya özel değer tutmaz.
def call(String serverName, Map options = [:]) {
    def bar = '═' * 60
    def dotnetRoot = options.dotnetRoot ?: env.DOTNET_ROOT ?: '/home/jenkins/.dotnet'

    def slnCandidates = sh(
        script: "find . -iname '*.sln' -not -path '*/node_modules/*' -not -path '*/bin/*' -not -path '*/obj/*'",
        returnStdout: true
    ).trim()
    def slnList = slnCandidates ? slnCandidates.split('\n') as List : []

    echo """
╔${bar}╗
║  🔍  SonarQube Analizi Başlatılıyor
╚${bar}╝
  🖥️  Sunucu : ${serverName}
  🧩 Tür    : ${slnList ? ".NET (${slnList.size()} adet .sln)" : 'generic (sonar-scanner)'}"""

    def t0 = System.currentTimeMillis()
    try {
        withSonarQubeEnv(serverName) {
            if (slnList) {
                // Birden fazla .sln varsa hepsi tek begin/end analizinde sırayla build edilir
                // (manuel seçim gerekmez).
                withEnv(["SLN_LIST=${slnList.join('\n')}", "DOTNET_ROOT=${dotnetRoot}"]) {
                    sh '''
                        set -e
                        export PATH="$DOTNET_ROOT:$DOTNET_ROOT/tools:$PATH"
                        SONAR_LOGIN="${SONAR_TOKEN:-$SONAR_AUTH_TOKEN}"
                        PROJECT_KEY="$(grep -m1 '^sonar.projectKey=' sonar-project.properties | cut -d= -f2-)"
                        PROJECT_NAME="$(grep -m1 '^sonar.projectName=' sonar-project.properties | cut -d= -f2-)"
                        SONAR_EXCLUSIONS="$(grep -m1 '^sonar.exclusions=' sonar-project.properties | cut -d= -f2-)"
                        SONAR_CPD_EXCLUSIONS="$(grep -m1 '^sonar.cpd.exclusions=' sonar-project.properties | cut -d= -f2-)"
                        SONAR_COVERAGE_EXCLUSIONS="$(grep -m1 '^sonar.coverage.exclusions=' sonar-project.properties | cut -d= -f2-)"
                        if [ -z "$PROJECT_KEY" ]; then
                            echo "⛔ sonar-project.properties içinde sonar.projectKey tanımlı değil"
                            exit 1
                        fi

                        # SonarScanner for .NET (v8+) sonar-project.properties dosyasını tanımıyor ve
                        # workspace'te görürse "end" adımında hata verip analizi düşürüyor. Değerleri
                        # yukarıda okuduktan sonra analiz süresince dosya geçici olarak kaldırılıyor.
                        mv sonar-project.properties .sonar-project.properties.bak
                        trap 'mv .sonar-project.properties.bak sonar-project.properties' EXIT

                        dotnet --version
                        unset VERSION Version PACKAGEVERSION PackageVersion

                        # Boş bir /d: değeri SonarScanner'da "format geçersiz" uyarısına yol açıyor,
                        # bu yüzden properties dosyasında tanımlı olmayan alanlar hiç geçilmiyor.
                        set -- /k:"$PROJECT_KEY" /n:"${PROJECT_NAME:-$PROJECT_KEY}" \
                          /d:sonar.host.url="$SONAR_HOST_URL" /d:sonar.token="$SONAR_LOGIN"
                        if [ -n "$SONAR_EXCLUSIONS" ]; then set -- "$@" /d:sonar.exclusions="$SONAR_EXCLUSIONS"; fi
                        if [ -n "$SONAR_CPD_EXCLUSIONS" ]; then set -- "$@" /d:sonar.cpd.exclusions="$SONAR_CPD_EXCLUSIONS"; fi
                        if [ -n "$SONAR_COVERAGE_EXCLUSIONS" ]; then set -- "$@" /d:sonar.coverage.exclusions="$SONAR_COVERAGE_EXCLUSIONS"; fi
                        dotnet sonarscanner begin "$@"
                        echo "$SLN_LIST" | while IFS= read -r sln; do
                            [ -n "$sln" ] || continue
                            echo "🔨 Build: $sln"
                            dotnet build "$sln" --no-incremental
                        done
                        dotnet sonarscanner end /d:sonar.token="$SONAR_LOGIN"
                    '''
                }
            } else {
                // .sln yoksa (PHP, Node, Java vb.): sonar-scanner kendi sonar-project.properties'ini okur.
                sh 'sonar-scanner'
            }
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
