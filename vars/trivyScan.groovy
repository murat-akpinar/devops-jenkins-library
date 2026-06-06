def call(String tag) {
    def CFG        = globalConfig()
    def dockerScanHost  = CFG.DOCKERSCAN_HOST
    def sshUser    = CFG.DOCKERSCAN_SSH_USER
    def scriptPath = CFG.DOCKERSCAN_SCRIPT_PATH
    def nexusCredentialId = env.NEXUS_CREDENTIAL_ID ?: ''

    echo "🔍 Trivy taraması başlatılıyor (tag: ${tag})..."
    def servicesConfig = readYaml file: 'services.yml'

    def runScan = { nexusUser, nexusPass ->
        servicesConfig.services.each { svc ->
            def key       = svc.name?.trim()?.toUpperCase()
            def imageName = svc.image_name ?: svc.name
            if (env."DO_${key}" == '1') {
                echo "  → [${imageName}:${tag}] taranıyor..."
                sh """ssh -o StrictHostKeyChecking=no ${sshUser}@${dockerScanHost} \
                    'NEXUS_USER="${nexusUser}" NEXUS_PASS="${nexusPass}" ${scriptPath} --image ${imageName} --tag ${tag}'"""
                echo "  ✅ [${imageName}:${tag}] tarama tamamlandı"
            } else {
                echo "  ⏭️ [${svc.name}]: build edilmedi → tarama atlanıyor."
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
        echo "ℹ️ Credential belirtilmedi → anonim tarama"
        runScan('', '')
    }

    echo "✅ Trivy taraması tamamlandı"
}
