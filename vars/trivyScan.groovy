def call(String tag) {
    def CFG        = globalConfig()
    def dockerScanHost  = CFG.DOCKERSCAN_HOST
    def sshUser    = CFG.DOCKERSCAN_SSH_USER
    def scriptPath = CFG.DOCKERSCAN_SCRIPT_PATH
    def nexusCredentialId = env.NEXUS_CREDENTIAL_ID ?: ''

    def bar = '═' * 60
    def servicesConfig = readYaml file: 'services.yml'

    echo """
╔${bar}╗
║  🔍  Trivy Taraması Başlatılıyor
╚${bar}╝
  🏷️  Tag     : ${tag}
  🖥️  Sunucu  : ${sshUser}@${dockerScanHost}
  📂 Script  : ${scriptPath}"""

    def t0 = System.currentTimeMillis()
    try {
        // Kimlik bilgileri bilerek Groovy ile interpolate EDİLMİYOR. Önceki sürümde gerçek
        // değerler ssh komut satırına gömülüyordu; hem Jenkins agent'ının hem de DockerScan
        // sunucusunun process listesinde (ps aux) görünüyorlardı. Artık stdin üzerinden
        // aktarılıyorlar — hiçbir argv'de yer almıyor, özel karakterli şifreler de bozulmuyor.
        def runScan = { boolean withAuth ->
            servicesConfig.services.each { svc ->
                def key       = svc.name?.trim()?.toUpperCase()
                def imageName = svc.image_name ?: svc.name
                if (env."DO_${key}" != '1') {
                    echo "  ⏭️  [${svc.name}]: build edilmedi → tarama atlanıyor."
                    return
                }
                echo "  ┌─ [${imageName}:${tag}] taranıyor..."
                if (withAuth) {
                    sh """
                        printf '%s\\n%s\\n' "\$NEXUS_USER" "\$NEXUS_PASS" |
                          ssh -o StrictHostKeyChecking=no -o ConnectTimeout=20 ${sshUser}@${dockerScanHost} 'read -r U; read -r P; NEXUS_USER="\$U" NEXUS_PASS="\$P" ${scriptPath} --image ${imageName} --tag ${tag}'
                    """
                } else {
                    sh """ssh -o StrictHostKeyChecking=no -o ConnectTimeout=20 ${sshUser}@${dockerScanHost} '${scriptPath} --image ${imageName} --tag ${tag}'"""
                }
                echo "  └─ ✅ [${imageName}:${tag}] tamamlandı"
            }
        }

        if (nexusCredentialId?.trim()) {
            withCredentials([usernamePassword(
                credentialsId: nexusCredentialId,
                usernameVariable: 'NEXUS_USER',
                passwordVariable: 'NEXUS_PASS'
            )]) {
                runScan(true)
            }
        } else {
            echo "  ℹ️  Credential belirtilmedi → anonim tarama"
            runScan(false)
        }

        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ✅  Trivy Taraması Tamamlandı  (${elapsed}s)
╚${bar}╝"""
    } catch (e) {
        def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
        echo """
╔${bar}╗
║  ❌  Trivy Taraması Başarısız  (${elapsed}s)
╚${bar}╝
  🔴 Hata   : ${e.message}
  🏷️  Tag    : ${tag}
  🖥️  Sunucu : ${sshUser}@${dockerScanHost}"""
        error "❌ [Trivy] Tarama başarısız: ${e.message}"
    }
}
