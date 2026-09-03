def call() {
    def bar = '═' * 60
    echo """
╔${bar}╗
║  🔑  Credentials Toplanıyor
╚${bar}╝"""

    def systemVarPrefixes = ['DO_', 'TAG_EXISTS_', 'JENKINS_', 'BUILD_', 'JOB_', 'NODE_', 'HUDSON_', 'GIT_', 'BRANCH_', 'CHANGE_', 'RUN_', 'STAGE_', 'SYSTEMD_',
                     // Kütüphanenin kendi geçici değişkenleri — asla credential değildir
                     'DEPLOY_', 'DS_', 'PS_', 'RT_']
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
                     'INVOCATION_ID', 'JAVA_HOME']

    def credentialsMap = [:]

    // Önceki sürüm her environment değişkeni için AYRI bir `printenv` adımı açıyordu. Jenkins'te
    // her `sh` bir Durable Task'tir (wrapper dosyası yazılır, process başlatılır, çıktı poll edilir);
    // 40-60 değişkenle bu, build başına dakikalar demekti. Artık tek adımda okunuyor.
    //
    // Değerler base64'lenerek satır tabanlı hale getiriliyor: çok satırlı credential'lar
    // (PEM anahtarı, sertifika, SSH key) düz `env` çıktısında ayrıştırılamaz — devam satırları
    // yeni bir değişken sanılır ya da değer ilk satırda kesilir.
    // `base64 | tr -d` hem GNU coreutils hem BusyBox'ta çalışır (`base64 -w0` yalnızca GNU'da).
    //
    // DİKKAT — davranış değişikliği: anahtar deseni artık `[A-Z_][A-Z_0-9]*`.
    // Önceki sürüm `^[A-Z_]+=` kullanıyordu ve bu, adında RAKAM geçen değişkenleri
    // (AWS_S3_SECRET, DB2_PASSWORD gibi) SESSİZCE ATLIYORDU — o credential'lar deploy
    // sunucusundaki .env dosyasına hiç yazılmıyordu. Artık toplanıyorlar.
    def dump = sh(returnStdout: true, script: '''
        for k in $(env | grep -E '^[A-Z_][A-Z_0-9]*=' | cut -d= -f1 | sort -u); do
            printf '%s\\t%s\\n' "$k" "$(printenv "$k" | base64 | tr -d '\\n')"
        done
    ''')

    dump.split('\n').each { line ->
        def idx = line.indexOf('\t')
        if (idx <= 0) return

        def key = line.substring(0, idx).trim()
        if (!key) return

        def isSystemVar = systemVarPrefixes.any { key.startsWith(it) } || systemVars.contains(key)
        if (isSystemVar) return

        try {
            def encoded = line.substring(idx + 1).trim()
            if (!encoded) return
            // .trim(): eski davranışla aynı — printenv'in eklediği son satır sonu atılır
            def value = new String(encoded.decodeBase64(), 'UTF-8').trim()

            if (value && !value.matches(/^\*+$/) && value != '${' + key + '}') {
                credentialsMap[key] = value
            }
        } catch (Exception e) {
            // Çözülemeyen değeri atla
        }
    }

    if (credentialsMap.size() > 0) {
        echo """
  ┌─ Sonuç
  │  📋 Toplam    : ${credentialsMap.size()} credential
  └─ 🔑 Anahtarlar : ${credentialsMap.keySet().join(', ')}"""
    } else {
        echo """
  └─ ⚠️  Hiç credential bulunamadı. Environment bloğunda credentials tanımlı mı kontrol edin."""
    }

    return credentialsMap
}
