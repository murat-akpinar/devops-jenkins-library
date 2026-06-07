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

    def nexusRegistry  = nexusRegistryUrl.replaceAll('^https?://', '')
    def nexusImageName = "${nexusRegistry}/repository/${registryPath}/${imageName}:${version}"

    def bar = '═' * 60
    echo """
╔${bar}╗
║  📥  İmaj Çekiliyor
╚${bar}╝
  📦 Servis  : ${serviceName}
  🖥️  Sunucu  : ${sshUser}@${deployIp}:${sshPort}
  🏷️  İmaj    : ${nexusImageName}"""

    def t0 = System.currentTimeMillis()
    try {
        def pullCmds = """
            { set +x; } 2>/dev/null; printf '  ┌─[1/4] Eski imaj temizleniyor...\\n'; { set -x; } 2>/dev/null
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker rmi ${imageName}:${version} || true"
            { set +x; } 2>/dev/null; printf '  ├─[2/4] İmaj çekiliyor...\\n'; { set -x; } 2>/dev/null
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker pull ${nexusImageName}"
            { set +x; } 2>/dev/null; printf '  ├─[3/4] İmaj etiketleniyor...\\n'; { set -x; } 2>/dev/null
            ssh -p ${sshPort} -o ConnectTimeout=20 ${sshUser}@${deployIp} "docker tag ${nexusImageName} ${imageName}:${version}"
            { set +x; } 2>/dev/null; printf '  └─[4/4] Nexus etiketi kaldırılıyor...\\n'; { set -x; } 2>/dev/null
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
                         && echo '  ✅ Docker login mevcut: ${nexusRegistry}' \
                         || (echo '  🔐 Docker login yapılıyor: ${nexusRegistry}' && \
                             printf '%s' '${NEXUS_PASS}' | docker login ${nexusRegistry} -u '${NEXUS_USER}' --password-stdin)"
                    ${pullCmds}
                """
            }
        } else {
            echo "  ℹ️  Credential belirtilmedi → anonim pull"
            sh pullCmds
        }

        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ✅  İmaj Çekildi  (${elapsed}s)
╚${bar}╝
  📦 ${imageName}:${version}"""
    } catch (e) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  İmaj Çekme Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata   : ${e.message}
  📦 Servis : ${serviceName}
  🖥️  Sunucu : ${sshUser}@${deployIp}:${sshPort}"""
        error "❌ [Pull] ${serviceName} imaj çekme başarısız: ${e.message}"
    }
}
