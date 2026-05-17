def call(String serviceName, String version, String nexusUrl, String registryPath, String credentialId = 'Nexus_Credentials') {
    def tagExists = false

    def checkScript = """
        RESULT=\$(curl -sf ${credentialId?.trim() ? '-u "$NEXUS_USER:$NEXUS_PASS"' : ''} \
            "${nexusUrl}/service/rest/v1/search?repository=${registryPath}&name=${serviceName}&version=${version}" 2>/dev/null)
        echo "\$RESULT" | grep -q '"version"'
    """

    if (credentialId?.trim()) {
        withCredentials([usernamePassword(
            credentialsId: credentialId,
            usernameVariable: 'NEXUS_USER',
            passwordVariable: 'NEXUS_PASS'
        )]) {
            tagExists = sh(script: checkScript, returnStatus: true) == 0
        }
    } else {
        tagExists = sh(script: checkScript, returnStatus: true) == 0
    }

    return tagExists
}
