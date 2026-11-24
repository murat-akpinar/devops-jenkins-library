import groovy.transform.Field

@Field boolean mukerrerKontrolYapildi = false

def call() {
    // Mükerrer build kontrolü: her Jenkinsfile Initialize'da globalConfig() çağırdığı için kanca
    // burada; her Jenkinsfile'a options { disableConcurrentBuilds() } eklemek gerekmiyor.
    // currentBuild.rawBuild KULLANILAMAZ: kütüphane sandbox'ta, RejectedAccessException verir.
    // Süren bir build'in result'ı null olur.
    if (!mukerrerKontrolYapildi) {
        mukerrerKontrolYapildi = true
        def onceki = currentBuild.previousBuild
        int bakilan = 0
        while (onceki != null && bakilan < 20) {
            if (onceki.result == null) {
                error "⛔ Bu pipeline'da #${onceki.number} hâlâ çalışıyor → mükerrer build başlatılmadı."
            }
            onceki = onceki.previousBuild
            bakilan++
        }
        // Build History listesi yalnız "#42" gösteriyor; sürüm biliniyorsa etikete yazılıyor.
        if (env.VERSION) {
            currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.VERSION}"
        }
    }

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
        CHECKOV_ENABLED     : true,                                 // false → adımı atla
        CHECKOV_SOFT_FAIL   : true,                                 // true → hata olsa pipeline devam eder
        CHECKOV_SCRIPT_PATH : '/app/DockerScan/trigger-checkov.sh', // uzak sunucudaki tetikleyici script

        // ── OSV-Scanner ──────────────────────────────────────────────────────
        OSV_ENABLED         : true,                                 // false → adımı atla
        OSV_SOFT_FAIL       : true,                                 // true → hata olsa pipeline devam eder
        OSV_SCRIPT_PATH     : '/app/DockerScan/trigger-osv.sh',     // uzak sunucudaki tetikleyici script

    ]
}
