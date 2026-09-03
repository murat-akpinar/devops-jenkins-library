// Jenkins agent'ında build sonrası disk temizliği yapar.
// Her Jenkinsfile'da zaten olan env.VERSION otomatik kullanılır — çağıran tarafın hiçbir şey
// vermesi gerekmez, dockerCleanup() her projede aynı şekilde çağrılır.
def call(Map config = [:]) {
    def keepVersion = (config.keepVersion?.toString()?.trim()) ?: env.VERSION?.trim()
    def servicesFile = config.servicesFile ?: (env.SERVICES_FILE?.trim() ?: 'services.yml')
    def bar = '═' * 60
    def t0 = System.currentTimeMillis()

    echo """
╔${bar}╗
║  🧹  Docker Cleanup Başlıyor
╚${bar}╝"""

    // `prune -af` tüm kullanılmayan imajları siliyordu → Deploy aşaması, Nexus'tan henüz
    // çekmediği imajları kaybedip patlıyordu. Sadece dangling imaj + build cache temizlenir,
    // tag'li imajlara dokunulmaz. Tam temizlik isteniyorsa bu adımı Deploy'dan SONRA çağırın.
    sh '''
        docker image prune -f || true
        docker builder prune -f || true
    '''

    // keepVersion verilirse: services.yml okunup HER servisin kendi imaj adı otomatik bulunur ve
    // şu an build edilen version dışındaki eski tag'leri silinir. Proje ismini Jenkinsfile'a elle
    // yazmaya gerek yoktur — services.yml zaten hangi imajların bu projeye ait olduğunu bilir.
    // Agent paylaşımlı olduğu için başka projelerin veya base imajların (node, dotnet vb.)
    // imajlarına hiç dokunulmaz.
    if (keepVersion && fileExists(servicesFile)) {
        def servicesConfig = readYaml file: servicesFile
        servicesConfig.services.each { svc ->
            def imageName = svc.image_name ?: svc.name
            echo "  ┌─ [${imageName}] eski tag'ler temizleniyor (korunan: ${keepVersion})"
            sh """
                docker images --format '{{.Repository}}:{{.Tag}}' --filter 'reference=${imageName}' \
                    | grep -v ':${keepVersion}\$' \
                    | xargs -r docker rmi -f || true
            """
        }
    } else if (keepVersion) {
        echo "  ℹ️  ${servicesFile} bulunamadı → eski tag temizliği atlandı"
    } else {
        echo "  ℹ️  keepVersion/env.VERSION yok → eski tag temizliği atlandı"
    }

    def elapsed = ((System.currentTimeMillis() - t0) / 1000).toInteger()
    echo """
╔${bar}╗
║  ✅  Docker Cleanup Tamamlandı  (${elapsed}s)
╚${bar}╝"""
}
