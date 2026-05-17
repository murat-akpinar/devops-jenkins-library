def call(String servicePath, String prevCommit, String currCommit) {
    int changed = 0
    
    if (prevCommit?.trim() && currCommit?.trim()) {
        def prevOk = (sh(script: "git cat-file -e ${prevCommit}^{commit}", returnStatus: true) == 0)
        def currOk = (sh(script: "git cat-file -e ${currCommit}^{commit}", returnStatus: true) == 0)
        
        if (prevOk && currOk) {
            changed = (sh(script: "git diff --quiet ${prevCommit}..${currCommit} -- ${servicePath}", returnStatus: true) != 0) ? 1 : 0
        }
    }
    
    return changed
}

