// Workspace'te .sln varsa .NET (dotnet-sonarscanner) akışı, yoksa klasik `sonar-scanner` çalıştırılır.
// Her iki durumda da proje anahtarı/adı reponun kendi sonar-project.properties dosyasından okunur;
// bu library hiçbir repo'ya özel değer tutmaz.
def call(String serverName, Map options = [:]) {
    // USE_EXISTING_IMAGE: bu build'de imaj üretilmiyor, registry'deki mevcut imaj deploy ediliyor →
    // kodu taramak anlamsız ve dakikalar sürüyor. Quality Gate de aynı koşulla atlanıyor.
    if (env.USE_EXISTING_IMAGE?.toString() == 'true') {
        echo "⏭️ USE_EXISTING_IMAGE aktif → SonarQube analizi atlandı."
        return
    }
    def bar = '═' * 60
    def dotnetRoot = options.dotnetRoot ?: env.DOTNET_ROOT ?: '/home/jenkins/.dotnet'

    // Scanner, JRE'yi ve plugin'leri ~/.sonar altına cache'liyor. Agent her build'de temiz
    // gelirse bu cache boş oluyor ve analiz öncesi 3-4 dk sadece indirmeye gidiyor.
    // Cache'i workspace'in değil agent kökünün altına alıp build'ler arası kalıcı yapıyoruz
    // (cleanWs burayı silmiyor). Dışarıdan SONAR_USER_HOME verilmişse ona dokunmuyoruz.
    // ponytail: agent kökü WORKSPACE'ten türetiliyor; farklı layout'ta SONAR_USER_HOME ver.
    def agentRoot = env.WORKSPACE?.replaceFirst(/\/workspace\/.*$/, '')
    env.SONAR_USER_HOME = env.SONAR_USER_HOME?.trim() ?: "${agentRoot ?: env.HOME}/.sonar"
    echo "  📦 Scanner cache : ${env.SONAR_USER_HOME}"
    // Cache build'ler arasi gercekten kaliyor mu? Her build'de "0 girdi" gorunuyorsa agent
    // ephemeral demektir (dizin her seferinde sifirdan olusuyor); scanner log'undaki
    // "User cache:" satiriyla da karsilastirin — farkli bir yol yaziyorsa property ulasmiyor.
    sh '''
        mkdir -p "$SONAR_USER_HOME"
        echo "  📦 Cache durumu  : $(ls "$SONAR_USER_HOME/cache" 2>/dev/null | wc -l) girdi, $(du -sh "$SONAR_USER_HOME" 2>/dev/null | cut -f1)"
    '''

    // .sln yoksa .csproj'a düşüyoruz: aksi halde C# repo'su düz sonar-scanner ile taranıyor,
    // cs dosyaları indexlenip tek kural çalışmıyor ve quality gate yarım veriyle karar veriyor
    // ("Your project contains C# files which cannot be analyzed"). dotnet build ikisini de alır.
    def findProjects = { pattern ->
        def out = sh(
            script: "find . -iname '${pattern}' -not -path '*/node_modules/*' -not -path '*/bin/*' -not -path '*/obj/*'",
            returnStdout: true
        ).trim()
        out ? out.split('\n') as List : []
    }
    // .slnx (yeni XML solution) da solution'dır. Aranmazsa csproj yoluna düşülüyor: repo'nun kendi
    // solution'ı yok sayılıp her csproj için ayrı `dotnet sln add` çalışıyordu.
    // İkisi birden varsa (.sln → .slnx geçişi) .sln kullanılır. `*.sln*` değil: *.sln.DotSettings'i de yakalar.
    def slnList = findProjects('*.sln') ?: findProjects('*.slnx')
    // .sln yoksa csproj'lar toplanır; shell tarafı bunları geçici bir sln'e koyup TEK build yapar.
    // (tek tek build edilince her csproj kendi ProjectReference'larını yeniden derliyordu).
    def makeSln = ''
    if (!slnList) { slnList = findProjects('*.csproj'); makeSln = slnList ? '1' : '' }

    echo """
╔${bar}╗
║  🔍  SonarQube Analizi Başlatılıyor
╚${bar}╝
  🖥️  Sunucu : ${serverName}
  🧩 Tür    : ${slnList ? ".NET (${slnList.size()} adet ${makeSln ? 'csproj' : 'solution'})" : 'generic (sonar-scanner)'}"""

    def t0 = System.currentTimeMillis()
    try {
        withSonarQubeEnv(serverName) {
            if (slnList) {
                // .NET projesi: birden fazla sln/csproj varsa hepsi tek begin/end analizinde sırayla
                // build edilir (manuel seçim gerekmez).
                withEnv(["SLN_LIST=${slnList.join('\n')}", "DOTNET_ROOT=${dotnetRoot}", "MAKE_SLN=${makeSln}"]) {
                    sh '''
                        set -e
                        export PATH="$DOTNET_ROOT:$DOTNET_ROOT/tools:$PATH"
                        SONAR_LOGIN="${SONAR_TOKEN:-$SONAR_AUTH_TOKEN}"
                        # Java properties okuması: '\\' ile biten satır sonrakiyle birleşir (baştaki boşluk atılır), CRLF temizlenir.
                        # Düz grep çok satırlı değerde yalnızca '\\' okuyordu → .NET projelerinde exclusions HİÇ uygulanmıyordu
                        # (büyük Migrations dizinleri her build'de taranıyordu).
                        prop() { tr -d '\\r' < sonar-project.properties | sed -e ':a' -e '/\\\\$/{N;s/\\\\\\n[[:space:]]*//;ba' -e '}' | grep -m1 "^$1=" | cut -d= -f2-; }
                        PROJECT_KEY="$(prop sonar.projectKey)"
                        PROJECT_NAME="$(prop sonar.projectName)"
                        SONAR_EXCLUSIONS="$(prop sonar.exclusions)"
                        SONAR_CPD_EXCLUSIONS="$(prop sonar.cpd.exclusions)"
                        SONAR_COVERAGE_EXCLUSIONS="$(prop sonar.coverage.exclusions)"
                        if [ -z "$PROJECT_KEY" ]; then
                            echo "⛔ sonar-project.properties içinde sonar.projectKey tanımlı değil"
                            exit 1
                        fi

                        # SonarScanner for .NET (v8+) sonar-project.properties dosyasını tanımıyor ve
                        # workspace'te görürse "end" adımında hata verip analizi düşürüyor. Değerleri
                        # yukarıda okuduktan sonra analiz süresince dosya geçici olarak kaldırılıyor.
                        mv sonar-project.properties .sonar-project.properties.bak
                        trap 'mv .sonar-project.properties.bak sonar-project.properties; rm -f sonar-tmp.sln sonar-tmp.slnx' EXIT

                        dotnet --version
                        unset VERSION Version PACKAGEVERSION PackageVersion

                        # Repoda .sln yoksa: csproj'ları geçici bir sln'e toplayıp tek build (bkz. Groovy tarafı).
                        # Yeni SDK'lar .slnx üretebiliyor, ikisi de aranıyor; dosya EXIT trap'inde siliniyor.
                        if [ -n "$MAKE_SLN" ]; then
                            N="$(echo "$SLN_LIST" | grep -c .)"
                            dotnet new sln -n sonar-tmp --force >/dev/null
                            TMP_SLN="$(ls sonar-tmp.sln sonar-tmp.slnx 2>/dev/null | head -1)"
                            # --in-root: SDK 10+ 'sln add' ProjectReference'ları da ekliyor; SDK 11 ise aynı kök
                            # klasörde aynı dosya adlı proje görünce (aynı proje olsa bile) hata veriyor
                            # ("Solution folder 'Backend' already contains a project with the filename ...").
                            # Kökte klasör olmadığı için bu kontrol atlanıyor, tekrar eklenen proje sessizce geçiliyor.
                            # ponytail: farklı dizinde aynı adlı iki csproj kökte çakışır, biri eklenmez; olursa --solution-folder ile ayır.
                            echo "$SLN_LIST" | while IFS= read -r p; do
                                [ -n "$p" ] && dotnet sln "$TMP_SLN" add --in-root "$p" >/dev/null
                            done
                            echo "🧩 .sln yok → $N csproj geçici $TMP_SLN içinde tek seferde build edilecek"
                            SLN_LIST="$TMP_SLN"
                        fi

                        # Boş bir /d: değeri SonarScanner'da "format geçersiz" uyarısına yol açıyor,
                        # bu yüzden properties dosyasında tanımlı olmayan alanlar hiç geçilmiyor.
                        # .NET scanner SONAR_USER_HOME env var'ını değil sonar.userHome property'sini
                        # okuyor; property geçilmezse JRE'yi ephemeral ~/.sonar'a her build indiriyor.
                        set -- /k:"$PROJECT_KEY" /n:"${PROJECT_NAME:-$PROJECT_KEY}" \
                          /d:sonar.host.url="$SONAR_HOST_URL" /d:sonar.token="$SONAR_LOGIN" \
                          /d:sonar.userHome="$SONAR_USER_HOME"
                        # Rapor yükleme (api/ce/submit) varsayılan 60sn'de kesiliyor; büyük raporda sunucu
                        # bu sürede cevap dönemiyor (~6MB rapor → SocketTimeoutException).
                        # Non-.NET dalıyla aynı değer; .NET scanner properties dosyasını okumadığı için burada veriliyor.
                        set -- "$@" /d:sonar.scanner.socketTimeout=300
                        if [ -n "$SONAR_EXCLUSIONS" ]; then set -- "$@" /d:sonar.exclusions="$SONAR_EXCLUSIONS"; fi
                        if [ -n "$SONAR_CPD_EXCLUSIONS" ]; then set -- "$@" /d:sonar.cpd.exclusions="$SONAR_CPD_EXCLUSIONS"; fi
                        if [ -n "$SONAR_COVERAGE_EXCLUSIONS" ]; then set -- "$@" /d:sonar.coverage.exclusions="$SONAR_COVERAGE_EXCLUSIONS"; fi
                        # Varsayılan KAPALI. Analizin nerede takıldığını görmek için projede SONAR_VERBOSE = '1':
                        # scanner her adımı/HTTP isteğini süresiyle döküyor.
                        if [ "${SONAR_VERBOSE:-0}" != "0" ]; then set -- "$@" /d:sonar.verbose=true; fi
                        dotnet sonarscanner begin "$@"
                        echo "$SLN_LIST" | while IFS= read -r sln; do
                            [ -n "$sln" ] || continue
                            echo "🔨 Build: $sln"
                            # MSBuild worker node'ları build bitince ayakta kalıyor. Aynı agent'ta sonraki build bu
                            # bayat node'ları devralınca farklı analyzer setiyle çöküyor (MSB4166). Reuse kapalı.
                            # Solution'da JavaScript projesi (.esproj) varsa build onlar için npm install / npm run build
                            # çalıştırıyordu: Sonar için gereksiz (JS/TS build'siz analiz ediliyor), süreyi uzatıyor ve
                            # agent'ta npm hata verirse build'i düşürüyor.
                            dotnet build "$sln" --no-incremental -nodeReuse:false -p:ShouldRunNpmInstall=false -p:ShouldRunBuildScript=false
                        done
                        dotnet sonarscanner end /d:sonar.token="$SONAR_LOGIN"
                    '''
                }
            } else {
                // .NET projesi değil (PHP, Node, vb.): sonar-scanner kendi sonar-project.properties'ini okur.
                // socketTimeout varsayılanı 60sn. Cache boşken indirilen engine jar'ı bu sürede
                // gelmiyor ve analiz "request timed out" ile düşüyor; cache dolduktan sonra
                // indirme olmadığı için bu değer devreye hiç girmiyor.
                // JRE: CLI 8.x linux-x64 paketi gömülü Java 21 getiriyor, SonarQube 26.x de 21 istiyor.
                // Onu kullanınca her build sunucudan ~50MB JRE inmiyor (log'da ~3 dk sürüyordu).
                // Gömülü JRE yoksa ya da 21'den eskiyse (CLI 7.x → 17) eskisi gibi provisioning'e bırakılır.
                sh '''
                    J="$(dirname "$(readlink -f "$(command -v sonar-scanner)")")/../jre/bin/java"
                    set -- -Dsonar.scanner.socketTimeout=300
                    if [ -x "$J" ] && "$J" -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])'; then
                        echo "  ☕ Gömülü JRE kullanılıyor, JRE indirme atlandı: $J"
                        set -- "$@" -Dsonar.scanner.skipJreProvisioning=true -Dsonar.scanner.javaExePath="$J"
                    fi
                    # .NET dalıyla aynı: varsayılan KAPALI, projede SONAR_VERBOSE = '1' ile açılır.
                    if [ "${SONAR_VERBOSE:-0}" != "0" ]; then set -- "$@" -X; fi
                    sonar-scanner "$@"
                '''
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
