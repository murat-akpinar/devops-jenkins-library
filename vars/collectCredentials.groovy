def call() {
    def bar = '═' * 60
    echo """
╔${bar}╗
║  🔑  Credentials Toplanıyor
╚${bar}╝"""

    def systemVarPrefixes = ['DO_', 'TAG_EXISTS_', 'JENKINS_', 'BUILD_', 'JOB_', 'NODE_', 'HUDSON_', 'GIT_', 'BRANCH_', 'CHANGE_', 'RUN_', 'STAGE_', 'SYSTEMD_',
                     // Kütüphanenin kendi geçici değişkenleri — asla credential değildir
                     'DEPLOY_', 'DS_', 'PS_', 'RT_',
                     // Jenkinsfile'ın Initialize adımında set ettiği pipeline değişkenleri.
                     // Tek tek listelemek yerine prefix: yeni bir GLOBAL_* eklendiğinde
                     // denylist'i güncellemeyi unutmak sızıntıya yol açıyordu.
                     'GLOBAL_', 'AGENT_',
                     // sonarQubeAnalysis global env'e SONAR_USER_HOME yazıyor; .env'e sızmasın
                     'SONAR_',
                     // Agent'ın locale/araç ortamı. Hiçbiri credential değil ve deploy host'unun
                     // .env'ine yazılırsa servisin kendi ayarını EZİYOR. DOTNET_ bilerek YOK: bir
                     // .NET projesi DOTNET_ENVIRONMENT'ı meşru olarak .env'de isteyebilir.
                     'LC_', 'XDG_', 'NVM_', 'npm_config_', 'MSBUILD']
    def systemVars = ['WORKSPACE', 'PWD', 'PATH', 'HOME', 'USER', 'SHELL', 'VERSION', 'APP',
                     'IP1', 'IP2', 'IP3', 'IP4', 'DEPLOY_TARGETS', 'PULL_EXISTING',
                     'NEXUS_URL', 'NEXUS_REGISTRY_URL', 'REGISTRY_PATH', 'SONAR_SERVER',
                     'NEXUS_CREDENTIAL_ID', 'HARBOR_CREDENTIAL_ID', 'HARBOR_REGISTRY',
                     'EXECUTOR_NUMBER', 'NODE_NAME', 'WORKSPACE_TMP', 'JENKINS_HOME', 'JENKINS_URL',
                     'LESSOPEN', 'LESSCLOSE', 'MAIL', 'CI', 'SHLVL', 'OLDPWD', 'SUDO_GID', 'SUDO_UID',
                     'SUDO_USER', 'SUDO_COMMAND', 'LOGNAME', '_', 'TERM', 'LANG', 'LS_COLORS', 'XDG_DATA_DIRS',
                     'VERSION_TAG', 'FORCE_REBUILD', 'USE_EXISTING_IMAGE', 'ENVIRONMENT',
                     'GLOBAL_TARGETS', 'GLOBAL_SSH_PORT', 'RECIPIENT_EMAIL', 'SERVICES_FILE',
                     'DOCKER_COMPOSE_FILE', 'DOCKER_COMPOSE_REMOTE_FILE', 'DOTNET_ROOT',
                     'SYSTEMD_EXEC_PID', 'JOURNAL_STREAM', 'MEMORY_PRESSURE_WATCH', 'MEMORY_PRESSURE_WRITE',
                     'INVOCATION_ID', 'JAVA_HOME', 'CLASSPATH',
                     // Agent/container ortam değişkenleri. Bunlar deploy sunucusundaki .env'e
                     // yazılırsa servisin kendi .env'indeki değeri EZİYOR: extraEnvVars yerel
                     // .env merge'ünden SONRA uygulanıyor. Örn. agent'ın TZ'si projenin
                     // TZ'sinin yerine geçiyordu.
                     'HOSTNAME', 'TZ', 'HOSTTYPE', 'PS1', 'PS2', 'IFS',
                     // Agent'ın proxy ayarları servise taşınmasın
                     'HTTP_PROXY', 'HTTPS_PROXY', 'NO_PROXY', 'FTP_PROXY', 'ALL_PROXY',
                     // sshagent(){} içinde çağrıldığında ortama giren ssh-agent değişkenleri:
                     // agent'a özgü yol/PID taşıyorlar, .env'e yazılmaları bilgi sızdırır.
                     'SSH_AUTH_SOCK', 'SSH_AGENT_PID', 'SSH_CONNECTION', 'SSH_CLIENT', 'SSH_TTY',
                     // Geçici dizin ve terminal/locale artıkları
                     'TMPDIR', 'TMP', 'TEMP', 'LANGUAGE', 'COLORTERM', 'DISPLAY']

    def credentialsMap = [:]

    // ADLAR tek bir `sh` adımından alınır (sh'a verilen env, environment{}/credentials()/withEnv
    // dahil etkin ortamın tamamıdır), DEĞERLER `env."AD"` ile Groovy'den okunur. Değer shell'den
    // hiç geçmiyor: base64/printenv yok, set -x sızıntısı yok, çok satırlı değer (PEM) bozulmuyor.
    //
    // env.getEnvironment() KULLANILMAZ: yalnızca temel ortam + env.X atamalarını döndürüyor,
    // environment{} bloğu ve credentials() adım bağlamında durduğu için hiç gelmiyor → credential
    // listesi boş kalıyor, .env'e yalnızca VERSION yazılıyordu. env."AD" adım bağlamını da okur.
    // Önceki `env | base64` çözümü bazı agent'larda sessizce başarısızdı ("0 credential").
    //
    // Anahtar deseni `[A-Za-z_][A-Za-z_0-9]*`. Küçük harf ŞART: .NET anahtarları
    // (ConnectionStrings__DefaultConnection, Jwt__SigningKey...) karışık harfli; eski `[A-Z_]...`
    // deseni bunları sessizce eliyordu. Sistem değişkeni filtresi bu yüzden harf duyarsız.
    // Çok satırlı bir değerin `AD=...` gibi görünen devam satırı sahte ad üretebilir; env."AD"
    // null döner ve aşağıdaki boş değer kontrolü onu eler.
    def adlar = sh(returnStdout: true, script: '''
        { set +x; } 2>/dev/null
        env | sed -n 's/^\\([A-Za-z_][A-Za-z_0-9]*\\)=.*/\\1/p' | sort -u
    ''').trim().split('\n')
    def adayAnahtar = 0
    def reddedilen = []   // yalnızca anahtar adı tutulur; değer asla loglanmaz
    adlar.each { k ->
        def key = k?.toString()?.trim()
        if (!key || !(key ==~ /[A-Za-z_][A-Za-z_0-9]*/)) return

        // Harf duyarsız (http_proxy == HTTP_PROXY). toUpperCase() değil equalsIgnoreCase:
        // tr locale'li JVM'de "i" → "İ" olup eşleşmeyi bozar.
        if (systemVarPrefixes.any { p -> key.length() >= p.length() && key.substring(0, p.length()).equalsIgnoreCase(p) } ||
            systemVars.any { it.equalsIgnoreCase(key) }) return

        def value = env."${key}"?.toString()?.trim()
        // Boş değer credential adayı değildir (agent'ta CLASSPATH= gibi boş sistem değişkenleri
        // var); aday sayılırsa credential'sız projelerde yanlış hata tetiklenirdi.
        if (!value) return
        adayAnahtar++

        // Maskelenmiş (***) ya da çözümsüz (${AD}) değilse ekle
        if (!value.matches(/^\*+$/) && value != '${' + key + '}') {
            credentialsMap[key] = value
        } else {
            reddedilen << key
        }
    }

    if (credentialsMap.size() > 0) {
        echo """
  ┌─ Sonuç
  │  📋 Toplam    : ${credentialsMap.size()} credential
  └─ 🔑 Anahtarlar : ${credentialsMap.keySet().join(', ')}"""
        if (reddedilen) {
            echo "  ⚠️ Değeri maskeli/çözümsüz olduğu için atlanan: ${reddedilen.join(', ')}"
        }
    } else if (adayAnahtar > 0) {
        // Aday anahtarlar var ama hepsi maskeli ya da çözümsüz. Eskiden yalnızca uyarı basılıp
        // devam ediliyordu; deploy başarılı görünüp .env eksik yazılıyor, sebep günler sonra bulunuyordu.
        error """⛔ ${adayAnahtar} credential adayı bulundu ama hiçbiri kullanılamadı — deploy durduruldu.
  Atlanan anahtarlar : ${reddedilen.join(', ')}
  ℹ️ Değerler maskeli (***) ya da çözümsüz (\${AD} biçiminde). Jenkinsfile environment{}
     bloğunu ve credential ID'lerini kontrol edin."""
    } else {
        // Meşru durum: projenin environment bloğunda hiç credential tanımlı değil.
        echo """
  └─ ℹ️  Credential tanımlı değil — .env yalnızca VERSION ile yazılacak."""
    }

    return credentialsMap
}
