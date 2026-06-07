def call(Map config) {
    def serviceName = config.serviceName
    def version = config.version
    def servicePath = config.servicePath
    def dockerfile = config.dockerfile ?: 'Dockerfile'
    def nexusUrl = config.nexusUrl
    def nexusRegistryUrl = config.nexusRegistryUrl ?: env.NEXUS_REGISTRY_URL ?: nexusUrl
    def registryPath = config.registryPath
    def jenkinsRegistry = config.jenkinsRegistry ?: env.HARBOR_REGISTRY ?: ''
    def envFile = config.envFile // opsiyonel
    def nexusRepoCredentialId  = config.nexusRepoCredentialId ?: env.NEXUS_CREDENTIAL_ID ?: 'Nexus_Credentials'
    def harborCredentialId     = config.harborCredentialId ?: env.HARBOR_CREDENTIAL_ID ?: ''

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
            echo "📄 ${envPath} bulundu, VERSION güncellendi"
        } else {
            content = "# Environment variables\nVERSION=${version}\n"
            echo "📄 ${envPath} bulunamadı, build için oluşturuldu"
        }
        writeFile file: envPath, text: content
    }

    echo "🚀 [${serviceName}] Build & Push başlıyor..."
    checkovScan(servicePath, serviceName, version)
    osvScan(servicePath, serviceName, version)

    def useNexusAuth = nexusRepoCredentialId?.trim()
    def useHarbor    = jenkinsRegistry?.trim() && harborCredentialId?.trim()
    def cacheFrom    = "${nexusRegistry}/repository/${registryPath}/${serviceName}:latest"

    def secretSetup = useNexusAuth ? """
        _NEXUS_USER_FILE=\$(mktemp)
        _NEXUS_PASS_FILE=\$(mktemp)
        printf '%s' "\$NEXUS_REPO_USER" > "\$_NEXUS_USER_FILE"
        printf '%s' "\$NEXUS_REPO_PASS" > "\$_NEXUS_PASS_FILE"
        _cleanup() { rm -f "\$_NEXUS_USER_FILE" "\$_NEXUS_PASS_FILE"; }
        trap _cleanup EXIT
    """ : ""

    def dockerBuildCommand = useNexusAuth
        ? "DOCKER_BUILDKIT=1 docker build --cache-from ${cacheFrom} --secret id=nexus_user,src=\"\$_NEXUS_USER_FILE\" --secret id=nexus_pass,src=\"\$_NEXUS_PASS_FILE\" -t ${serviceName}:${version} -f ${dockerfile} ."
        : "DOCKER_BUILDKIT=1 docker build --cache-from ${cacheFrom} -t ${serviceName}:${version} -f ${dockerfile} ."

    def shellScript = """
        set -e
        echo "[${serviceName}] Build & Push ${serviceName}:${version}"

        # .env dosyası kontrolü (varsa)
        ${hasEnvFile ? """
        if [ ! -f ${envFile} ]; then
            echo "❌ ${envFile} dosyası bulunamadı! Build için ${envFile} dosyası gereklidir."
            exit 1
        fi
        echo "✅ ${envFile} dosyası mevcut"
        """ : ""}

        ${secretSetup}

        cd ${servicePath}
        ${dockerBuildCommand}
        docker tag ${serviceName}:${version} ${nexusRegistry}/repository/${registryPath}/${serviceName}:${version}
        docker push ${nexusRegistry}/repository/${registryPath}/${serviceName}:${version}
        docker tag ${serviceName}:${version} ${nexusRegistry}/repository/${registryPath}/${serviceName}:latest
        docker push ${nexusRegistry}/repository/${registryPath}/${serviceName}:latest

        ${useHarbor ? """
        echo "🔐 Harbor login yapılıyor: ${jenkinsRegistry}"
        printf '%s' "\$HARBOR_PASS" | docker login ${jenkinsRegistry.split('/')[0]} -u "\$HARBOR_USER" --password-stdin
        docker pull ${nexusRegistry}/repository/${registryPath}/${serviceName}:${version}
        docker tag ${nexusRegistry}/repository/${registryPath}/${serviceName}:${version} ${jenkinsRegistry}/${serviceName}:${version}
        docker push ${jenkinsRegistry}/${serviceName}:${version}
        """ : "# Harbor kullanılmıyor, push atlandı"}

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

    if (bindings) {
        withCredentials(bindings) { sh shellScript }
    } else {
        sh shellScript
    }

    echo "✅ [${serviceName}] Build & Push tamamlandı"
}
