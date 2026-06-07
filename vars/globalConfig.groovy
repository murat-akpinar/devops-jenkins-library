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

        // ── DockerScan ───────────────────────────────────────────────────────
        DOCKERSCAN_HOST         : 'YOUR_DOCKERSCAN_HOST_IP',    // DockerScan sunucu IP
        DOCKERSCAN_SSH_USER     : 'your-user',                  // SSH kullanıcısı
        DOCKERSCAN_SCRIPT_PATH  : '/app/DockerScan/trigger-nexus.sh',
        DOCKERSCAN_BACKEND_PORT : '3018',                       // DockerScan backend API portu (BACKEND_PORT)

        // ── Checkov ──────────────────────────────────────────────────────────
        CHECKOV_ENABLED               : true,                                 // false → adımı atla
        CHECKOV_SOFT_FAIL             : true,                                 // true → hata olsa pipeline devam eder
        CHECKOV_SCRIPT_PATH           : '/app/DockerScan/trigger-checkov.sh', // uzak sunucudaki tetikleyici script
        CHECKOV_QUALITY_GATE_MAX_FAILED: 0,                                   // izin verilen maks başarısız check sayısı

        // ── OSV-Scanner ──────────────────────────────────────────────────────
        OSV_ENABLED              : true,                                 // false → adımı atla
        OSV_SOFT_FAIL            : true,                                 // true → hata olsa pipeline devam eder
        OSV_SCRIPT_PATH          : '/app/DockerScan/trigger-osv.sh',     // uzak sunucudaki tetikleyici script
        OSV_QUALITY_GATE_GRADE   : 'C',                                  // A-F arası eşik notu (A=en iyi)

    ]
}
