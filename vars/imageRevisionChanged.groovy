// Bir servis, ŞU AN <version> tag'ini taşıyan imajın build edildiği commit'ten bu yana
// değişti mi? Baseline `GIT_PREVIOUS_SUCCESSFUL_COMMIT` DEĞİL, imaja basılmış
// 'org.opencontainers.image.revision' label'ıdır.
//
// NİÇİN VAR: `git submodule update --remote` kullanan pipeline'larda superproject'in
// gitlink pointer'ı bump'lanmaz. `checkDiff`'in submodule dalı pinlenmiş (bayat) SHA ile
// submodule HEAD'ini karşılaştırdığı için ikisi ASLA eşit olmaz: her build "değişti" der,
// atlama mekanizması hiç çalışmaz ve değişmemiş servisler de sıfırdan build edilir.
// Bu fonksiyon baseline'ı git yerine registry'den okur, pointer bump'ı gerektirmez.
//
// `checkDiff` yerine KULLANILMAZ, onun alternatifidir: `--remote` kullanmayan, pointer'ı
// düzgün pinlenen repolarda `checkDiff` doğru çalışır ve registry'ye hiç gitmez.
//
// Kullanım:
//   def changed = imageRevisionChanged([
//       imageName   : resolveImageName(svc.name, svc.image_name),
//       version     : env.VERSION,
//       path        : svc.path,
//       nexusRegistryUrl: env.NEXUS_REGISTRY_URL,
//       registryPath: env.REGISTRY_PATH
//   ])
//
// true döner (= build et) şu durumlarda:
//   - imajda label yok → bu özelliği devreye aldıktan sonraki İLK BUILD'de her servis
//     bir kez build edilir; sonraki build'lerden itibaren atlama çalışır
//   - label'daki commit bu klonda/submodule'de bulunamıyor
//   - gerçekten değişiklik var
def call(Map config) {
    def imageName = config.imageName
    def version = config.version
    def path = config.path?.toString()?.trim()
    def nexusRegistryUrl = config.nexusRegistryUrl ?: env.NEXUS_REGISTRY_URL ?: config.nexusUrl
    def registryPath = config.registryPath
    def labelKey = config.labelKey ?: 'org.opencontainers.image.revision'
    def credentialId = config.credentialId

    if (!imageName?.toString()?.trim() || !version?.toString()?.trim() || !path) {
        echo "⚠️ imageRevisionChanged: imageName/version/path eksik → değişti kabul edildi"
        return true
    }

    def builtSha = imageLabelOnNexus(imageName.toString(), version.toString(), labelKey, nexusRegistryUrl, registryPath, credentialId)
    if (!builtSha) {
        echo "ℹ️ [${imageName}:${version}] imajda '${labelKey}' label'ı yok → değişti kabul edildi"
        return true
    }

    def currentSha = servicePathRevision(path)
    if (!currentSha) {
        echo "⚠️ [${path}] mevcut commit okunamadı → değişti kabul edildi"
        return true
    }

    if (builtSha == currentSha) {
        return false
    }

    def escaped = path.replace("'", "'\\''")
    def isSubmodule = (sh(script: "test -f '${escaped}/.git'", returnStatus: true) == 0)

    // SHA'lar farklı olsa bile ilgili path'te içerik değişmemiş olabilir (ör. başka bir
    // servisin dizini değişmiş). Karar içerik diff'ine göre verilir.
    if (isSubmodule) {
        // Label submodule'ün kendi commit'ini taşır → karşılaştırma submodule reposunda.
        if (sh(script: "git -C '${escaped}' cat-file -e ${builtSha}^{commit}", returnStatus: true) != 0) {
            echo "ℹ️ [${path}] label'daki commit ${builtSha.take(8)} submodule klonunda yok → değişti kabul edildi"
            return true
        }
        return sh(script: "git -C '${escaped}' diff --quiet ${builtSha} HEAD", returnStatus: true) != 0
    }

    // Normal dizin: label superproject commit'ini taşır → path'e kapsamlı diff.
    if (sh(script: "git cat-file -e ${builtSha}^{commit}", returnStatus: true) != 0) {
        echo "ℹ️ [${path}] label'daki commit ${builtSha.take(8)} bu klonda yok → değişti kabul edildi"
        return true
    }
    return sh(script: "git diff --quiet ${builtSha}..HEAD -- '${escaped}'", returnStatus: true) != 0
}
