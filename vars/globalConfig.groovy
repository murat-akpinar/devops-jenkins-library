def call() {
    return [

        // ── Nexus ────────────────────────────────────────────────────────────
        NEXUS_URL           : 'http://YOUR_NEXUS_IP:8081',  // REST API (tag kontrol)
        NEXUS_REGISTRY_URL  : 'http://YOUR_NEXUS_IP:8090',  // Docker push/pull/login
        REGISTRY_PATH       : 'your-registry',              // Docker repository adı
        NEXUS_CREDENTIAL_ID : 'Nexus_Credentials',          // Jenkins credential ID (anonim erişimde: '')

        // ── Harbor ───────────────────────────────────────────────────────────
        // Kullanılmıyorsa üç satırı da boş bırakın: ''
        HARBOR_URL           : '',                          // örn: 'http://YOUR_HARBOR_IP:7000'
        HARBOR_REGISTRY_PATH : '',                          // Harbor proje adı, örn: 'your-project'
        HARBOR_CREDENTIAL_ID : '',                          // örn: 'Harbor_Credentials'

        // ── SonarQube ────────────────────────────────────────────────────────
        SONAR_SERVER        : 'your-sonar-server',          // Jenkins SonarQube server adı

        // ── Trivy ────────────────────────────────────────────────────────────
        TRIVY_HOST          : 'YOUR_TRIVY_HOST_IP',         // Trivy dashboard sunucu IP
        TRIVY_SSH_USER      : 'your-user',                  // SSH kullanıcısı
        TRIVY_SCRIPT_PATH   : '/app/trivy-dashboard/trigger-nexus.sh',

    ]
}
