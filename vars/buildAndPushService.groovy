def call(Map config) {
    def serviceName = config.serviceName
    def version = config.version
    def servicePath = config.servicePath
    def dockerfile = config.dockerfile ?: 'Dockerfile'
    def target = config.target // opsiyonel: multi-stage Dockerfile'da build edilecek stage
    def nexusUrl = config.nexusUrl
    def nexusRegistryUrl = config.nexusRegistryUrl ?: env.NEXUS_REGISTRY_URL ?: nexusUrl
    def registryPath = config.registryPath
    def jenkinsRegistry = config.jenkinsRegistry ?: env.HARBOR_REGISTRY ?: ''
    def envFile = config.envFile // opsiyonel
    def nexusRepoCredentialId  = config.nexusRepoCredentialId ?: env.NEXUS_CREDENTIAL_ID ?: 'Nexus_Credentials'
    def harborCredentialId     = config.harborCredentialId ?: env.HARBOR_CREDENTIAL_ID ?: ''
    // latest tag'i push'lamak build cache'ini (--cache-from) besler; kapatmak için pushLatest: false
    def pushLatest = (config.pushLatest == null) ? true : (config.pushLatest.toString() == 'true')

    // Docker push/pull adresi (registry portu, örn: 8090)
    def nexusRegistry = nexusRegistryUrl.replaceAll('^https?://', '')

    // envFile null kontrolü
    def hasEnvFile = envFile && envFile.toString().trim() && envFile != 'null'

    // envFile belirtilmişse, build öncesi VERSION satırını garanti et (yoksa oluştur, varsa güncelle)
    if (hasEnvFile) {
        def envPath = envFile.toString().trim()
        def content = ""
        if (fileExists(envPath)) {
            content = readFile(envPath)
            if (content.contains("VERSION=")) {
                content = content.replaceAll(/(?m)^VERSION=.*$/, "VERSION=${version}")
            } else {
                content += (content.endsWith("\n") ? "" : "\n") + "VERSION=${version}\n"
            }
            echo "  📄 ${envPath} bulundu, VERSION güncellendi"
        } else {
            content = "# Environment variables\nVERSION=${version}\n"
            echo "  📄 ${envPath} bulunamadı, build için oluşturuldu"
        }
        writeFile file: envPath, text: content
    }

    def useNexusAuth = nexusRepoCredentialId?.trim()
    def useHarbor    = jenkinsRegistry?.trim() && harborCredentialId?.trim()
    def versionImage = "${nexusRegistry}/repository/${registryPath}/${serviceName}:${version}"
    def latestImage  = "${nexusRegistry}/repository/${registryPath}/${serviceName}:latest"
    def cacheFrom    = latestImage

    def bar = '═' * 60
    def t0  = System.currentTimeMillis()
    echo """
╔${bar}╗
║  🚀  Build & Push Başlıyor
╚${bar}╝
  📦 Servis  : ${serviceName}:${version}
  🖥️  Nexus   : ${nexusRegistry}/repository/${registryPath}/
  ${target ? "🎯  Stage   : ${target}" : "🎯  Stage   : (tüm Dockerfile)"}
  ${useHarbor ? "🚢  Harbor  : ${jenkinsRegistry}" : "🚢  Harbor  : kullanılmıyor"}"""

    def secretSetup = useNexusAuth ? """
        _NEXUS_USER_FILE=\$(mktemp)
        _NEXUS_PASS_FILE=\$(mktemp)
        printf '%s' "\$NEXUS_REPO_USER" > "\$_NEXUS_USER_FILE"
        printf '%s' "\$NEXUS_REPO_PASS" > "\$_NEXUS_PASS_FILE"
        _cleanup() { rm -f "\$_NEXUS_USER_FILE" "\$_NEXUS_PASS_FILE"; }
        trap _cleanup EXIT
    """ : ""

    // multi-stage Dockerfile'da yalnızca belirtilen stage build edilir (ör. target: 'runtime')
    def targetArg = target?.toString()?.trim() ? "--target ${target} " : ""

    // BUILDKIT_INLINE_CACHE=1 olmadan --cache-from sessizce ise yaramiyor: push edilen imajlar
    // inline cache metadata'si tasimadigi icin BuildKit registry'den okuyacak bir sey bulamiyor.
    // Bu build-arg ile :latest artik cache metadata'si tasiyor ve --cache-from gercekten calisiyor.
    def cacheArgs = "--cache-from ${cacheFrom} --build-arg BUILDKIT_INLINE_CACHE=1 "

    def dockerBuildCommand = useNexusAuth
        ? "DOCKER_BUILDKIT=1 docker build ${cacheArgs}${targetArg}--secret id=nexus_user,src=\"\$_NEXUS_USER_FILE\" --secret id=nexus_pass,src=\"\$_NEXUS_PASS_FILE\" -t ${serviceName}:${version} -f ${dockerfile} ."
        : "DOCKER_BUILDKIT=1 docker build ${cacheArgs}${targetArg}-t ${serviceName}:${version} -f ${dockerfile} ."

    def shellScript = """
        set -e

        ${hasEnvFile ? """
        { set +x; } 2>/dev/null; printf '  ┌─[1/4] .env dosyası kontrol ediliyor...\n'; { set -x; } 2>/dev/null
        if [ ! -f ${envFile} ]; then
            echo "❌ ${envFile} dosyası bulunamadı! Build için ${envFile} dosyası gereklidir."
            exit 1
        fi
        { set +x; } 2>/dev/null; printf '  └─ ✅ ${envFile} mevcut\n'; { set -x; } 2>/dev/null
        """ : ""}

        ${secretSetup}

        ${useNexusAuth ? """
        # Agent'in Nexus oturumu. Onceden bu adim yoktu ve agent'in pipeline disinda (elle ya da
        # provisioning ile) login edilmis olmasi varsayiliyordu; yeni bir agent eklendiginde push
        # "unauthorized" ile patliyor ve sebebi belirsiz gorunuyordu. Ayrica --cache-from ozel
        # registry'den cache manifest'i cekecegi icin bu oturuma ihtiyac duyar.
        # printf bir kabuk yerlesigi oldugu icin sifre hicbir process listesinde gorunmez.
        { set +x; } 2>/dev/null; printf '  ┌─[1/5] Nexus registry login...\n'
        printf '%s' "\$NEXUS_REPO_PASS" | docker login ${nexusRegistry} -u "\$NEXUS_REPO_USER" --password-stdin
        printf '  └─ ✅ Login tamamlandı\n'; { set -x; } 2>/dev/null
        """ : ""}

        { set +x; } 2>/dev/null; printf '  ┌─[2/4] Docker image build ediliyor...\n'; { set -x; } 2>/dev/null
        cd ${servicePath}
        ${dockerBuildCommand}
        { set +x; } 2>/dev/null; printf '  └─ ✅ Build tamamlandı\n'; { set -x; } 2>/dev/null

        { set +x; } 2>/dev/null; printf '  ┌─[3/4] Nexus registry push ediliyor...\n'; { set -x; } 2>/dev/null
        docker tag ${serviceName}:${version} ${versionImage}
        docker push ${versionImage}
        ${pushLatest ? """
        # latest'i yerel kısa isimden DEĞİL, az önce push edilen Nexus tag'inden üret.
        # Paralel job / cleanup sonrası ${serviceName}:${version} local'de kalmayabiliyor.
        docker tag ${versionImage} ${latestImage} || (docker pull ${versionImage} && docker tag ${versionImage} ${latestImage})
        docker push ${latestImage}
        """ : """
        echo "  ⏭️ latest tag atlandı (pushLatest=false)"
        """}
        { set +x; } 2>/dev/null; printf '  └─ ✅ Nexus push tamamlandı\n'; { set -x; } 2>/dev/null

        ${useHarbor ? """
        { set +x; } 2>/dev/null; printf '  ┌─[4/4] Harbor registry push ediliyor...\n'; { set -x; } 2>/dev/null
        printf '%s' "\$HARBOR_PASS" | docker login ${jenkinsRegistry.split('/')[0]} -u "\$HARBOR_USER" --password-stdin
        docker pull ${versionImage}
        docker tag ${versionImage} ${jenkinsRegistry}/${serviceName}:${version}
        docker push ${jenkinsRegistry}/${serviceName}:${version}
        { set +x; } 2>/dev/null; printf '  └─ ✅ Harbor push tamamlandı\n'; { set -x; } 2>/dev/null
        """ : ""}

        cd ..

        # Build sonrası .env dosyasını temizle (güvenlik)
        ${hasEnvFile ? "rm -f ${envFile}" : ""}
    """

    def bindings = []
    if (nexusRepoCredentialId?.trim()) {
        bindings << usernamePassword(credentialsId: nexusRepoCredentialId, usernameVariable: 'NEXUS_REPO_USER', passwordVariable: 'NEXUS_REPO_PASS')
    }
    if (useHarbor) {
        bindings << usernamePassword(credentialsId: harborCredentialId, usernameVariable: 'HARBOR_USER', passwordVariable: 'HARBOR_PASS')
    }

    try {
        if (bindings) {
            withCredentials(bindings) { sh shellScript }
        } else {
            sh shellScript
        }
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ✅  Build & Push Tamamlandı  (${elapsed}s)
╚${bar}╝
  📦 ${serviceName}:${version}"""
    } catch (e) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  Build & Push Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata   : ${e.message}
  📦 Servis : ${serviceName}:${version}
  🖥️  Nexus  : ${nexusRegistry}/repository/${registryPath}/"""
        error "❌ [Build] ${serviceName} build & push başarısız: ${e.message}"
    }
}
