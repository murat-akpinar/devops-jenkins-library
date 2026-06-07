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
        def runScan = { nexusUser, nexusPass ->
            servicesConfig.services.each { svc ->
                def key       = svc.name?.trim()?.toUpperCase()
                def imageName = svc.image_name ?: svc.name
                if (env."DO_${key}" == '1') {
                    echo "  ┌─ [${imageName}:${tag}] taranıyor..."
                    sh """ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} \
                        'NEXUS_USER="${nexusUser}" NEXUS_PASS="${nexusPass}" ${scriptPath} --image ${imageName} --tag ${tag}'"""
                    echo "  └─ ✅ [${imageName}:${tag}] tamamlandı"
                } else {
                    echo "  ⏭️  [${svc.name}]: build edilmedi → tarama atlanıyor."
                }
            }
        }

        if (nexusCredentialId?.trim()) {
            withCredentials([usernamePassword(
                credentialsId: nexusCredentialId,
                usernameVariable: 'NEXUS_USER',
                passwordVariable: 'NEXUS_PASS'
            )]) {
                runScan(NEXUS_USER, NEXUS_PASS)
            }
        } else {
            echo "  ℹ️  Credential belirtilmedi → anonim tarama"
            runScan('', '')
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
