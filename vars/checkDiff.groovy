// servicePath tek bir yol ya da yol listesi olabilir. Herhangi biri değişmişse 1 döner.
//
// options.failOpen — baseline (önceki başarılı commit) yoksa ne yapılacağı:
//   false (varsayılan) → 0 döner, yani "değişiklik yok". Geriye dönük uyumlu davranış.
//   true               → 1 döner, yani "bilinmiyor, güvenli tarafta kal ve build et".
// Varsayılanın 0 olması aslında ters yönde güvenli: ilk build'de ya da
// GIT_PREVIOUS_SUCCESSFUL_COMMIT yokken "hiçbir şey değişmedi" denir ve hiçbir servis
// deploy edilmez. Example/Jenkins_example.groovy bunu çağıran tarafta hasBaseline
// kontrolüyle telafi ediyor; fonksiyonu doğrudan kullanan pipeline'lar failOpen: true
// vermelidir. Varsayılan bilerek değiştirilmedi — kırıcı olurdu.
def call(servicePath, String prevCommit, String currCommit, Map options = [:]) {
    def failOpen = (options.failOpen?.toString() == 'true')
    def paths = ((servicePath instanceof Collection) ? servicePath : [servicePath])
        .collect { it?.toString()?.trim() }
        .findAll { it }

    // Yol verilmediğinde önceki sürümdeki davranış korunur: tüm repo kontrol edilir
    if (!paths) {
        paths = ['.']
    }

    if (!prevCommit?.trim() || !currCommit?.trim()) {
        return failOpen ? 1 : 0
    }

    def prevOk = (sh(script: "git cat-file -e ${prevCommit}^{commit}", returnStatus: true) == 0)
    def currOk = (sh(script: "git cat-file -e ${currCommit}^{commit}", returnStatus: true) == 0)

    if (!prevOk || !currOk) {
        return failOpen ? 1 : 0
    }

    // "--remote" ile checkout edilen submodule path'leri, superproject'in gitlink pointer'ı hiç
    // bump'lanmadan da kendi reposunda yeni commit alabilir. Bu yüzden submodule path'ler için
    // superproject diff'i yerine, submodule'ün şu anki (checkout edilmiş) HEAD'i superproject'te
    // en son PİNLENMİŞ SHA ile karşılaştırılır — pointer bump şart değildir.

    // .gitmodules'te tanımlı submodule path'leri. Checkout edilmemiş bir submodule'de
    // '<path>/.git' olmadığı için isSubmodule false dönüyor ve path sıradan dizin gibi
    // superproject diff'ine giriyordu: pointer bump'lanmamışsa "değişiklik yok" denip servis
    // hiç build edilmiyordu. Artık bu durum bilinmeyen kabul edilip güvenli tarafta kalınıyor.
    def declaredSubmodules = []
    if (fileExists('.gitmodules')) {
        declaredSubmodules = sh(
            script: "git config --file .gitmodules --get-regexp '^submodule\\..*\\.path\$' | awk '{print \$2}' || true",
            returnStdout: true
        ).trim().readLines().collect { it.trim() }.findAll { it }
    }

    for (p in paths) {
        def escaped = p.replace("'", "'\''")
        def isSubmodule = (sh(script: "test -f '${escaped}/.git'", returnStatus: true) == 0)

        if (!isSubmodule && declaredSubmodules.contains(p)) {
            echo "⚠️ [${p}] .gitmodules'te submodule olarak tanımlı ama checkout edilmemiş → değişti kabul edildi"
            return 1
        }

        if (isSubmodule) {
            def pinnedSha = sh(script: "git ls-tree ${prevCommit} -- '${escaped}' | awk '{print \$3}'", returnStdout: true).trim()
            if (!pinnedSha) {
                return 1 // superproject'te bu path prevCommit'te yoktu (yeni eklenmiş/rename) → değişti say
            }
            def pinnedKnown = (sh(script: "git -C '${escaped}' cat-file -e ${pinnedSha}^{commit}", returnStatus: true) == 0)
            if (!pinnedKnown) {
                return 1 // eski SHA submodule'de yoksa güvenli tarafta kal, değişti say
            }
            if (sh(script: "git -C '${escaped}' diff --quiet ${pinnedSha} HEAD", returnStatus: true) != 0) {
                return 1
            }
        } else {
            if (sh(script: "git diff --quiet ${prevCommit}..${currCommit} -- '${escaped}'", returnStatus: true) != 0) {
                return 1
            }
        }
    }

    return 0
}
