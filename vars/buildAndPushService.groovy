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
    def buildArgs = config.buildArgs ?: [:] // opsiyonel: Dockerfile ARG'larına --build-arg olarak geçilir
    def labels = config.labels ?: [:]       // opsiyonel: imaja --label olarak basılır (imageLabelOnNexus ile geri okunur)
    def pullBase = (config.pullBase?.toString() == 'true') // opsiyonel: --pull, base imajı her build'de yenile
    // --no-cache: config.noCache verilmezse FORCE_REBUILD parametresine düşer; böylece her projenin
    // Jenkinsfile'ını elden geçirmeye gerek kalmaz (booleanParam env'e 'true'/'false' olarak gelir).
    def noCache = config.containsKey('noCache') ? (config.noCache?.toString() == 'true') : (env.FORCE_REBUILD?.toString() == 'true')
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
            // Satır başı şart: contains("VERSION=") API_VERSION= gibi bir satırda da true dönüyor,
            // replaceAll hiçbir şeyi değiştirmiyor ve VERSION satırı hiç eklenmiyordu.
            if ((content =~ /(?m)^VERSION=/).find()) {
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
    """ : ""

    // Secret dosyaları ve build için üretilen .env TEK bir EXIT trap'inde silinir. Önceden
    // `rm -f <envFile>` script'in sonundaydı: `set -e` yüzünden build/push başarısız olunca hiç
    // çalışmıyor, .env workspace'te kalıyordu. envFile yolu cd'den önce absolute'a çevrilir.
    def cleanupLines = []
    if (useNexusAuth) { cleanupLines << 'rm -f "$_NEXUS_USER_FILE" "$_NEXUS_PASS_FILE"' }
    if (hasEnvFile)   { cleanupLines << 'rm -f "$_ENV_FILE_ABS"' }

    def envFileAbs = hasEnvFile ? """
        _ENV_FILE_ABS="${envFile}"
        case "\$_ENV_FILE_ABS" in /*) ;; *) _ENV_FILE_ABS="\$PWD/\$_ENV_FILE_ABS" ;; esac
    """ : ""

    def cleanupSetup = cleanupLines ? """
        _cleanup() { ${cleanupLines.join('; ')}; }
        trap _cleanup EXIT
    """ : ""

    // multi-stage Dockerfile'da yalnızca belirtilen stage build edilir (ör. target: 'runtime')
    def targetArg = target?.toString()?.trim() ? "--target ${target} " : ""

    // BUILDKIT_INLINE_CACHE=1 olmadan --cache-from sessizce ise yaramıyor: push edilen imajlar
    // inline cache metadata'si taşımadığı için BuildKit registry'den okuyacak bir şey bulamıyor.
    // Bu build-arg ile :latest artık cache metadata'si taşıyor ve --cache-from gerçekten çalışıyor.
    def cacheArgs = noCache ? "" : "--cache-from ${cacheFrom} --build-arg BUILDKIT_INLINE_CACHE=1 "

    // Değer tek tırnakta: boşluk ya da $ içeren bir değer shell'de bölünmesin/genişlemesin
    def quoteArgs = { String flag, Map m ->
        m.collect { k, v ->
            def key = k?.toString()?.trim()
            if (!key) { return null }
            def value = (v == null) ? '' : v.toString()
            "${flag} ${key}='${value.replace("'", "'\\''")}' "
        }.findAll { it }.join('')
    }

    // Kaynak commit HER imaja otomatik basılır (standart OCI label'ı): "bu imaj hangi commit'ten
    // build edildi?" sorusu registry'den cevaplanabilir; imageRevisionChanged bunu baseline olarak
    // kullanır. Çağıran labels ile kendi değerini verirse o kazanır. Label imaj config'ine yazıldığı
    // için layer cache'ini geçersiz kılmaz.
    def revisionLabel = 'org.opencontainers.image.revision'
    if (!labels.containsKey(revisionLabel)) {
        def autoRevision = servicePathRevision(servicePath)
        if (autoRevision) {
            labels = [(revisionLabel): autoRevision] + labels
        }
    }

    def buildArgsArg = quoteArgs('--build-arg', buildArgs)
    def labelsArg    = quoteArgs('--label', labels)
    if (buildArgsArg) { echo "  🔧 Build args : ${buildArgs.keySet().join(', ')}" }
    if (labelsArg)    { echo "  🏷️ Label'lar : ${labels.keySet().join(', ')}" }

    // --pull varsayılan KAPALI: agent'ta lokal build edilmiş (registry'de olmayan) bir base
    // imajdan FROM alan projelerde --pull build'i düşürür.
    def pullArg    = pullBase ? '--pull ' : ''
    def noCacheArg = noCache ? '--no-cache ' : ''
    if (noCache) { echo "  ♻️ Cache kapalı (--no-cache): tüm katmanlar yeniden build edilecek" }

    def extraArgs = "${pullArg}${noCacheArg}${targetArg}${buildArgsArg}${labelsArg}"

    def dockerBuildCommand = useNexusAuth
        ? "DOCKER_BUILDKIT=1 docker build ${cacheArgs}${extraArgs}--secret id=nexus_user,src=\"\$_NEXUS_USER_FILE\" --secret id=nexus_pass,src=\"\$_NEXUS_PASS_FILE\" -t ${serviceName}:${version} -f ${dockerfile} ."
        : "DOCKER_BUILDKIT=1 docker build ${cacheArgs}${extraArgs}-t ${serviceName}:${version} -f ${dockerfile} ."

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

        ${envFileAbs}
        ${secretSetup}
        ${cleanupSetup}

        ${useNexusAuth ? """
        # Agent'ın Nexus oturumu. Bu adım BEST-EFFORT: başarısız olursa build DURMAZ.
        # Önceden bu adım hiç yoktu ve agent'ın pipeline dışında (elle ya da provisioning
        # ile) login edilmiş olması varsayılıyordu. Adımı ZORUNLU yapmak, Docker realm'i
        # kapalı ya da kullanıcısının docker repo yetkisi olmayan kurulumlarda ÇALIŞAN
        # pipeline'ları kırıyor. Bu yüzden yalnızca deneniyor; gerçek doğrulama noktası
        # aşağıdaki `docker push` adımıdır. Login başarılıysa --cache-from da özel
        # registry'den cache manifest'i çekebilir.
        # printf bir kabuk yerleşiği olduğu için şifre hiçbir process listesinde görünmez.
        { set +x; } 2>/dev/null; printf '  ┌─[1/5] Nexus registry login deneniyor...\\n'
        if printf '%s' "\$NEXUS_REPO_PASS" | docker login ${nexusRegistry} -u "\$NEXUS_REPO_USER" --password-stdin; then
            printf '  └─ ✅ Login tamamlandı\\n'
        else
            printf '  └─ ⚠️  Login başarısız — mevcut oturum/anonim erişimle devam ediliyor.\\n'
            printf '       Push da başarısız olursa Nexus > Security > Realms altında\\n'
            printf '       "Docker Bearer Token Realm" aktif mi ve kullanıcının ilgili\\n'
            printf '       docker repository yetkisi var mı kontrol edin.\\n'
        fi
        { set -x; } 2>/dev/null
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

        cd "\$OLDPWD" # 'cd ..' servicePath '.' ya da çok seviyeli olduğunda yanlış dizine çıkıyordu

        # Build için üretilen .env ve secret dosyaları yukarıdaki EXIT trap'inde silinir
        # (başarısız build'de de çalışması için).
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
