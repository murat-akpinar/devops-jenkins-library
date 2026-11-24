def call(Map config) {
    def serviceName       = config.serviceName
    def SSH_OPTS = sshOptions()
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

    // Testin exit kodu korunmalı ama imaj her durumda temizlenmeli. Önceki sürümde script
    // `set -e` ile başlıyordu; `docker run` başarısız olunca kabuk o satırda çıkıyor,
    // altındaki TEST_EXIT/temizlik satırları hiç çalışmıyordu. Artık temizlik `trap` ile
    // garanti altında ve exit kodu `if` bloğuyla açıkça yakalanıyor.
    def testCmds = """
        SSH="ssh -p ${sshPort} ${SSH_OPTS} ${sshUser}@${deployIp}"

        cleanup() {
            echo "🧹 Test imajı temizleniyor..."
            \$SSH "docker rmi ${imageName} 2>/dev/null || true"
        }
        trap cleanup EXIT

        echo "📥 Nexus'tan imaj çekiliyor: ${imageName}"
        if ! \$SSH "docker pull ${imageName}"; then
            echo "❌ Imaj çekilemedi: ${imageName}"
            exit 1
        fi

        echo "🧪 Test başlatılıyor..."
        # Test komutu Groovy ile komut dizesine GÖMÜLMÜYOR, stdin ile aktarılıyor: içinde \$,
        # backtick ya da tek tırnak geçen komut agent tarafında genişliyor/tırnakları kırıyordu.
        # Test komutu TEK SATIR olmalıdır (uzak tarafta `read -r` bir satır okur).
        if printf '%s\\n' "\$RT_CMD" | \$SSH 'read -r CMD; docker run --rm ${imageName} sh -c "\$CMD"'; then
            echo "✅ Tüm testler başarıyla tamamlandı"
        else
            TEST_EXIT=\$?
            echo "❌ Testler başarısız (exit: \$TEST_EXIT)"
            exit \$TEST_EXIT
        fi
    """

    if (nexusCredentialId?.trim()) {
        // Kimlik bilgileri bilerek Groovy ile interpolate EDİLMİYOR — stdin üzerinden aktarılıyor,
        // böylece hiçbir process listesinde görünmüyor ve özel karakterli şifreler bozulmuyor.
        withEnv([
            "RT_SSH_TARGET=${sshUser}@${deployIp}",
            "RT_SSH_PORT=${sshPort}",
            "RT_REGISTRY=${nexusRegistry}",
                "RT_SSH_OPTS=${SSH_OPTS}"
        ]) {
            withCredentials([usernamePassword(
                credentialsId: nexusCredentialId,
                usernameVariable: 'NEXUS_USER',
                passwordVariable: 'NEXUS_PASS'
            )]) {
                sh '''
                    set -e
                    if ssh -p "$RT_SSH_PORT" $RT_SSH_OPTS "$RT_SSH_TARGET" "grep -qF '$RT_REGISTRY' ~/.docker/config.json 2>/dev/null"; then
                        echo "✅ Docker login mevcut: $RT_REGISTRY"
                    else
                        echo "🔐 Docker login yapılıyor: $RT_REGISTRY"
                        printf '%s\\n%s\\n%s\\n' "$RT_REGISTRY" "$NEXUS_USER" "$NEXUS_PASS" |
                          ssh -p "$RT_SSH_PORT" $RT_SSH_OPTS "$RT_SSH_TARGET" 'read -r R; read -r U; read -r P; printf "%s" "$P" | docker login "$R" -u "$U" --password-stdin'
                    fi
                '''
                withEnv(["RT_CMD=${testCommand}"]) { sh testCmds }
            }
        }
    } else {
        echo "ℹ️ Credential belirtilmedi → anonim pull"
        withEnv(["RT_CMD=${testCommand}"]) { sh testCmds }
    }

    echo "✅ [${serviceName}] Testler tamamlandı"
}
