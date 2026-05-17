def call(Map config) {
    def serviceName       = config.serviceName
    def version           = config.version
    def nexusUrl          = config.nexusUrl
    def nexusRegistryUrl  = config.nexusRegistryUrl ?: env.NEXUS_REGISTRY_URL ?: nexusUrl
    def registryPath      = config.registryPath
    def deployIp          = config.deployIp
    def testCommand       = config.testCommand ?: 'echo "Test komutu belirtilmedi"'
    def sshUser           = config.sshUser ?: 'your-user'
    def sshPort           = config.sshPort ?: 22
    def nexusCredentialId = config.nexusCredentialId ?: env.NEXUS_CREDENTIAL_ID ?: 'Nexus_Credentials'

    echo "🧪 [${serviceName}] Testler container içinde çalıştırılıyor..."

    def nexusRegistry = nexusRegistryUrl.replaceAll('^https?://', '')
    def imageName = "${nexusRegistry}/repository/${registryPath}/${serviceName}:${version}"

    def testCmds = """
        set -e
        echo "📥 Nexus'tan imaj çekiliyor: ${imageName}"
        ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker pull ${imageName}" || {
            echo "❌ Imaj çekilemedi: ${imageName}"
            exit 1
        }
        echo "🧪 Test başlatılıyor..."
        ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker run --rm ${imageName} sh -c '${testCommand}'"
        TEST_EXIT=\$?
        ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker rmi ${imageName} 2>/dev/null || true"
        [ \$TEST_EXIT -eq 0 ] || { echo "❌ Testler başarısız (exit: \$TEST_EXIT)"; exit \$TEST_EXIT; }
        echo "✅ Tüm testler başarıyla tamamlandı"
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
                     || (echo '🔐 Docker login yapılıyor...' && \
                         printf '%s' '${NEXUS_PASS}' | docker login ${nexusRegistry} -u '${NEXUS_USER}' --password-stdin)"
                ${testCmds}
            """
        }
    } else {
        echo "ℹ️ Credential belirtilmedi → anonim pull"
        sh testCmds
    }

    echo "✅ [${serviceName}] Testler tamamlandı"
}

