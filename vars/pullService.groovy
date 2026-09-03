def call(Map config) {
    def serviceName       = config.serviceName
    def SSH_OPTS = sshOptions()
    def imageName         = config.imageName ?: serviceName
    def version           = config.version
    def deployIp          = config.deployIp
    def nexusUrl          = config.nexusUrl
    def nexusRegistryUrl  = config.nexusRegistryUrl ?: env.NEXUS_REGISTRY_URL ?: nexusUrl
    def registryPath      = config.registryPath
    def sshUser = config.sshUser ?: globalConfig().DEPLOY_SSH_USER ?: 'your-user'
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
            { set +x; } 2>/dev/null; printf '  ┌─[1/4] Eski imaj temizleniyor...\n'; { set -x; } 2>/dev/null
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker rmi ${imageName}:${version} || true"
            { set +x; } 2>/dev/null; printf '  ├─[2/4] İmaj çekiliyor...\n'; { set -x; } 2>/dev/null
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker pull ${nexusImageName}"
            { set +x; } 2>/dev/null; printf '  ├─[3/4] İmaj etiketleniyor...\n'; { set -x; } 2>/dev/null
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker tag ${nexusImageName} ${imageName}:${version}"
            { set +x; } 2>/dev/null; printf '  └─[4/4] Nexus etiketi kaldırılıyor...\n'; { set -x; } 2>/dev/null
            ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp} "docker rmi ${nexusImageName} || true"
        """

        if (nexusCredentialId?.trim()) {
            // Kimlik bilgileri bilerek Groovy ile interpolate EDİLMİYOR. Değerler stdin üzerinden
            // uzak kabuğa aktarılıyor; böylece ne Jenkins agent'ının ne de deploy sunucusunun
            // process listesinde (ps aux) görünüyorlar. Ayrıca içinde tek tırnak / $ / boşluk
            // geçen şifreler de bozulmadan iletiliyor.
            withEnv([
                "PS_SSH_TARGET=${sshUser}@${deployIp}",
                "PS_SSH_PORT=${sshPort}",
                "PS_REGISTRY=${nexusRegistry}",
                "PS_SSH_OPTS=${SSH_OPTS}"
            ]) {
                withCredentials([usernamePassword(
                    credentialsId: nexusCredentialId,
                    usernameVariable: 'NEXUS_USER',
                    passwordVariable: 'NEXUS_PASS'
                )]) {
                    sh '''
                        set -e
                        if ssh -p "$PS_SSH_PORT" $PS_SSH_OPTS "$PS_SSH_TARGET" "grep -q '$PS_REGISTRY' ~/.docker/config.json 2>/dev/null"; then
                            echo "  ✅ Docker login mevcut: $PS_REGISTRY"
                        else
                            echo "  🔐 Docker login yapılıyor: $PS_REGISTRY"
                            printf '%s\\n%s\\n%s\\n' "$PS_REGISTRY" "$NEXUS_USER" "$NEXUS_PASS" |
                              ssh -p "$PS_SSH_PORT" $PS_SSH_OPTS "$PS_SSH_TARGET" 'read -r R; read -r U; read -r P; printf "%s" "$P" | docker login "$R" -u "$U" --password-stdin'
                        fi
                    '''
                    sh pullCmds
                }
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
