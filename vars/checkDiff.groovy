// servicePath tek bir yol ya da yol listesi olabilir. Herhangi biri değişmişse 1 döner.
def call(servicePath, String prevCommit, String currCommit) {
    def paths = ((servicePath instanceof Collection) ? servicePath : [servicePath])
        .collect { it?.toString()?.trim() }
        .findAll { it }

    // Yol verilmediğinde önceki sürümdeki davranış korunur: tüm repo kontrol edilir
    if (!paths) {
        paths = ['.']
    }

    if (!prevCommit?.trim() || !currCommit?.trim()) {
        return 0
    }

    def prevOk = (sh(script: "git cat-file -e ${prevCommit}^{commit}", returnStatus: true) == 0)
    def currOk = (sh(script: "git cat-file -e ${currCommit}^{commit}", returnStatus: true) == 0)

    if (!prevOk || !currOk) {
        return 0
    }

    // "--remote" ile checkout edilen submodule path'leri, superproject'in gitlink pointer'ı hiç
    // bump'lanmadan da kendi reposunda yeni commit alabilir. Bu yüzden submodule path'ler için
    // superproject diff'i yerine, submodule'ün şu anki (checkout edilmiş) HEAD'i superproject'te
    // en son PİNLENMİŞ SHA ile karşılaştırılır — pointer bump şart değildir.
    for (p in paths) {
        def escaped = p.replace("'", "'\''")
        def isSubmodule = (sh(script: "test -f '${escaped}/.git'", returnStatus: true) == 0)

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
