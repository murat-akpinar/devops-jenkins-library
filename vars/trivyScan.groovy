def call(String tag) {
    def CFG        = globalConfig()
    def trivyHost  = CFG.TRIVY_HOST ?: 'YOUR_TRIVY_HOST_IP'
    def sshUser    = CFG.TRIVY_SSH_USER ?: 'your-user'
    def scriptPath = CFG.TRIVY_SCRIPT_PATH ?: '/app/trivy-dashboard/trigger-nexus.sh'
    def nexusCredentialId = env.NEXUS_CREDENTIAL_ID ?: ''

    echo "🔍 Trivy taraması başlatılıyor (tag: ${tag})..."
    def servicesConfig = readYaml file: 'services.yml'

    def runScan = { nexusUser, nexusPass ->
        servicesConfig.services.each { svc ->
            def key       = svc.name?.trim()?.toUpperCase()
            def imageName = svc.image_name ?: svc.name
            if (env."DO_${key}" == '1') {
                echo "  → [${imageName}:${tag}] taranıyor..."
                sh """ssh -o StrictHostKeyChecking=no ${sshUser}@${trivyHost} \
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
