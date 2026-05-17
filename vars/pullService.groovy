def call(Map config) {
    def serviceName       = config.serviceName
    def imageName         = config.imageName ?: serviceName
    def version           = config.version
    def deployIp          = config.deployIp
    def nexusUrl          = config.nexusUrl
    def nexusRegistryUrl  = config.nexusRegistryUrl ?: env.NEXUS_REGISTRY_URL ?: nexusUrl
    def registryPath      = config.registryPath
    def sshUser           = config.sshUser ?: 'your-user'
    def sshPort           = config.sshPort ?: 22
    def nexusCredentialId = config.nexusCredentialId ?: env.NEXUS_CREDENTIAL_ID ?: 'Nexus_Credentials'

    def nexusRegistry = nexusRegistryUrl.replaceAll('^https?://', '')
    def nexusImageName = "${nexusRegistry}/repository/${registryPath}/${imageName}:${version}"

    echo "📥 [${serviceName} @ ${deployIp}] Imaj çekiliyor: ${nexusImageName} → ${imageName}:${version}"

    def pullCmds = """
        ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker rmi ${imageName}:${version} || true"
        ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker pull ${nexusImageName}"
        ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker tag ${nexusImageName} ${imageName}:${version}"
        ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker rmi ${nexusImageName} || true"
    """

    if (nexusCredentialId?.trim()) {
        withCredentials([usernamePassword(
            credentialsId: nexusCredentialId,
            usernameVariable: 'NEXUS_USER',
            passwordVariable: 'NEXUS_PASS'
        )]) {
            sh """
                ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} \
                    "grep -q '${nexusRegistry}' ~/.docker/config.json 2>/dev/null \
                     && echo '✅ Docker login mevcut: ${nexusRegistry}' \
                     || (echo '🔐 Docker login yapılıyor: ${nexusRegistry}' && \
                         printf '%s' '${NEXUS_PASS}' | docker login ${nexusRegistry} -u '${NEXUS_USER}' --password-stdin)"
                ${pullCmds}
            """
        }
    } else {
        echo "ℹ️ Credential belirtilmedi → anonim pull"
        sh pullCmds
    }

    echo "✅ [${serviceName} @ ${deployIp}] Imaj çekildi ve tag'lendi: ${imageName}:${version}"
}

