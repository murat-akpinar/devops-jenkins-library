def call() {
    def systemVarPrefixes = ['DO_', 'TAG_EXISTS_', 'JENKINS_', 'BUILD_', 'JOB_', 'NODE_', 'HUDSON_', 'GIT_', 'BRANCH_', 'CHANGE_', 'RUN_', 'STAGE_', 'SYSTEMD_']
    def systemVars = ['WORKSPACE', 'PWD', 'PATH', 'HOME', 'USER', 'SHELL', 'VERSION', 'APP', 
                     'IP1', 'IP2', 'IP3', 'IP4', 'DEPLOY_TARGETS', 'PULL_EXISTING', 
                     'NEXUS_URL', 'NEXUS_REGISTRY_URL', 'REGISTRY_PATH', 'SONAR_SERVER',
                     'NEXUS_CREDENTIAL_ID', 'HARBOR_CREDENTIAL_ID', 'HARBOR_REGISTRY',
                     'EXECUTOR_NUMBER', 'NODE_NAME', 'WORKSPACE_TMP', 'JENKINS_HOME', 'JENKINS_URL',
                     'LESSOPEN', 'LESSCLOSE', 'MAIL', 'CI', 'SHLVL', 'OLDPWD', 'SUDO_GID', 'SUDO_UID',
                     'SUDO_USER', 'SUDO_COMMAND', 'LOGNAME', '_', 'TERM', 'LANG', 'LS_COLORS', 'XDG_DATA_DIRS',
                     'VERSION_TAG', 'FORCE_REBUILD', 'USE_EXISTING_IMAGE', 'ENVIRONMENT',
                     'GLOBAL_TARGETS', 'RECIPIENT_EMAIL',
                     'SYSTEMD_EXEC_PID', 'JOURNAL_STREAM', 'MEMORY_PRESSURE_WATCH', 'MEMORY_PRESSURE_WRITE',
                     'INVOCATION_ID', 'JAVA_HOME']
    
    def credentialsMap = [:]
    
    // Pipeline environment bloğunda tanımlanan credentials'ları topla
    // Shell komutu ile her bir environment değişkenini kontrol et
    def envKeys = sh(script: 'env | grep -E "^[A-Z_]+=" | cut -d= -f1', returnStdout: true).trim()
    
    if (envKeys) {
        envKeys.split('\n').each { key ->
            key = key.trim()
            if (key && !key.isEmpty()) {
                // Sistem değişkenlerini filtrele
                def isSystemVar = systemVarPrefixes.any { key.startsWith(it) } || systemVars.contains(key)
                
                if (!isSystemVar) {
                    try {
                        // Shell komutu ile değişkenin değerini al
                        // printenv kullanarak değeri al (Jenkins maskelenmiş olsa bile çalışır)
                        def value = sh(script: "printenv ${key} || echo ''", returnStdout: true).trim()
                        
                        // Değer varsa ve maskelenmiş değilse ekle
                        // Jenkins credentials'ları maskeler ama printenv ile gerçek değer alınır
                        if (value && !value.isEmpty() && !value.matches(/^\*+$/) && value != '${' + key + '}') {
                            credentialsMap[key] = value
                        }
                    } catch (Exception e) {
                        // Değişken yoksa veya erişilemiyorsa atla
                    }
                }
            }
        }
    }
    
    echo "📋 Toplanan credentials sayısı: ${credentialsMap.size()}"
    if (credentialsMap.size() > 0) {
        echo "📋 Credentials: ${credentialsMap.keySet().join(', ')}"
    } else {
        echo "⚠️ Hiç credential bulunamadı. Environment bloğunda credentials tanımlı mı kontrol edin."
    }
    
    return credentialsMap
}

