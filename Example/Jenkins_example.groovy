@Library('devops-jenkins-library') _

pipeline {
    agent { label "linux" }

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['test', 'preprod', 'prod'], description: 'Deploy ortamını seçin')
        booleanParam(name: 'FORCE_REBUILD', defaultValue: false, description: 'Tüm imajları zorla yeniden build et')
        booleanParam(name: 'USE_EXISTING_IMAGE', defaultValue: false, description: 'Tag mevcutsa diff olsa bile build/push atla, mevcut imajı kullan')
        string(name: 'VERSION_TAG', defaultValue: 'test-v1.0', description: 'Deploy edilecek imaj tag (örn: test-v1.0)')
    }

    environment {
        VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"
    }

    options { timeout(time: 19999, unit: 'SECONDS') }

    stages {
        stage('Initialize') {
            steps {
                script {
                    def CFG = globalConfig()
                    env.NEXUS_URL            = CFG.NEXUS_URL
                    env.NEXUS_REGISTRY_URL   = CFG.NEXUS_REGISTRY_URL ?: CFG.NEXUS_URL
                    env.REGISTRY_PATH        = CFG.REGISTRY_PATH
                    env.SONAR_SERVER         = CFG.SONAR_SERVER
                    env.NEXUS_CREDENTIAL_ID  = CFG.NEXUS_CREDENTIAL_ID  ?: 'Nexus_Credentials'
                    env.HARBOR_CREDENTIAL_ID = CFG.HARBOR_CREDENTIAL_ID ?: ''
                    // Harbor registry adresini parçalardan hesapla: HOST/PROJECT
                    def harborHost = CFG.HARBOR_URL?.trim() ? CFG.HARBOR_URL.replaceAll('^https?://', '') : ''
                    def harborPath = CFG.HARBOR_REGISTRY_PATH?.trim() ?: ''
                    env.HARBOR_REGISTRY = (harborHost && harborPath) ? "${harborHost}/${harborPath}" : ''
                    if (!fileExists('services.yml')) {
                        error "❌ services.yml dosyası bulunamadı!"
                    }
                    def servicesConfig = readYaml file: 'services.yml'
                    env.APP            = servicesConfig.app_name ?: error("❌ services.yml içinde 'app_name' tanımlı değil!")
                    env.RECIPIENT_EMAIL = servicesConfig.recipients ?: ''
                    def envConfig = servicesConfig.environments?."${params.ENVIRONMENT}"
                    if (!envConfig) error "❌ '${params.ENVIRONMENT}' ortamı services.yml içinde tanımlı değil!"
                    env.GLOBAL_TARGETS = envConfig.deploy_targets
                        ? envConfig.deploy_targets.collect { it.toString().trim() }.join(',')
                        : ''
                    env.GLOBAL_SSH_PORT = envConfig.ssh_port?.toString() ?: '22'
                    echo "ℹ️ Kullanılacak VERSION: ${env.VERSION}"
                    echo "ℹ️ Ortam: ${params.ENVIRONMENT}"
                    echo "ℹ️ APP: ${env.APP}"
                    echo "ℹ️ Deploy hedefleri: ${env.GLOBAL_TARGETS}"
                    echo "✅ services.yml dosyası okundu"
                }
            }
        }

        stage('SonarQube Analysis') { steps { sonarQubeAnalysis(env.SONAR_SERVER) } }
        stage('SonarQube Quality Gate') { steps { sonarQubeQualityGate() } }

        stage('Tag Check on Nexus') {
            steps {
                script {
                    boolean force = (params.FORCE_REBUILD?.toString() == 'true')
                    boolean useExisting = (params.USE_EXISTING_IMAGE?.toString() == 'true')
                    if (force) { echo "🔁 FORCE_REBUILD aktif, tag kontrolü atlandı."; return }
                    def servicesConfig = readYaml file: 'services.yml'
                    def tagExistsMap = [:]
                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        def exists = checkTagOnNexus(imageName, env.VERSION, env.NEXUS_URL, env.REGISTRY_PATH, env.NEXUS_CREDENTIAL_ID ?: '')
                        tagExistsMap[key] = exists ? '1' : '0'
                        echo "🗂️ Tag kontrolü [${svc.name}]: ${exists ? 'VAR' : 'YOK'}"
                    }
                    tagExistsMap.each { key, value -> setTagExists(key, value) }
                    if (useExisting) {
                        def allTagsExist = tagExistsMap.every { it.value == '1' }
                        if (!allTagsExist) { error "⛔ USE_EXISTING_IMAGE=true seçili fakat bazı tag'ler bulunamadı." }
                    }
                }
            }
        }

        stage('Diff Check') {
            steps {
                script {
                    boolean force = (params.FORCE_REBUILD?.toString() == 'true')
                    boolean useExisting = (params.USE_EXISTING_IMAGE?.toString() == 'true')
                    env.PULL_EXISTING = '0'
                    def servicesConfig = readYaml file: 'services.yml'
                    def prev = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
                    def curr = env.GIT_COMMIT
                    def prevOk = prev?.trim() && (sh(script: "git cat-file -e ${prev}^{commit}", returnStatus: true) == 0)
                    def currOk = curr?.trim() && (sh(script: "git cat-file -e ${curr}^{commit}", returnStatus: true) == 0)
                    def hasBaseline = prevOk && currOk
                    if (useExisting) {
                        servicesConfig.services.each { svc -> setDoFlag(normalizeKey(svc.name), '0') }
                        env.PULL_EXISTING = '1'
                        echo "♻️ USE_EXISTING_IMAGE=true → diff olsa bile build/push atlanacak, mevcut imaj çekilecek."
                    } else {
                        servicesConfig.services.each { svc ->
                            def key = normalizeKey(svc.name)
                            def exists = tagExists(key)
                            if (force || !exists || !hasBaseline) {
                                setDoFlag(key, '1')
                                echo "🔨 [${svc.name}]: Tag yok, FORCE_REBUILD aktif veya önceki commit bulunamadı → build yapılacak"
                            } else {
                                def changed = checkDiff(svc.path, prev, curr)
                                setDoFlag(key, (changed == 1) ? '1' : '0')
                                echo "🔍 [${svc.name}]: Tag mevcut → diff kontrolü yapıldı, değişiklik ${changed == 1 ? 'VAR' : 'YOK'}"
                            }
                        }
                    }
                    echo "🔁 Final Karar:"
                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        echo "  - ${svc.name}: ${doFlag(key) == '1' ? 'Build yapılacak' : 'Tag mevcut ve değişiklik yok → atlanacak'}"
                    }
                }
            }
        }

        stage('Build & Push Services') {
            steps {
                script {
                    def servicesConfig = readYaml file: 'services.yml'
                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        if (doFlag(key) == '1') {
                            buildAndPushService([
                                serviceName      : imageName,
                                version          : env.VERSION,
                                servicePath      : svc.path,
                                dockerfile       : svc.dockerfile ?: 'Dockerfile',
                                nexusUrl         : env.NEXUS_URL,
                                nexusRegistryUrl : env.NEXUS_REGISTRY_URL,
                                registryPath     : env.REGISTRY_PATH,
                                jenkinsRegistry  : env.HARBOR_REGISTRY,
                                envFile          : svc.env_file
                            ])
                        } else {
                            echo "⏭️ [${svc.name}]: değişiklik yok — build & push atlanıyor."
                        }
                    }
                }
            }
        }

        stage('Checkov IaC Scan')         { steps { checkovScan(env.VERSION, env.APP) } }
        stage('OSV-Scanner Scan')         { steps { osvScan(env.VERSION, env.APP) } }
        stage('DockerScan Scan')          { steps { trivyScan(env.VERSION) } }
        stage('DockerScan Quality Gate')  { steps { trivyQualityGate(env.APP, 'C') } }

        stage('Docker Cleanup') { steps { sh 'docker system prune -af || true' } }

        stage('Run Tests') {
            steps {
                script {
                    def servicesConfig = readYaml file: 'services.yml'
                    def targetList = resolveTargets(null)
                    def testTarget = targetList ? targetList[0] : null
                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        if (doFlag(key) == '1' && svc.test_command && testTarget) {
                            runTestsInContainer([
                                serviceName      : resolveImageName(svc.name, svc.image_name),
                                version          : env.VERSION,
                                nexusUrl         : env.NEXUS_URL,
                                nexusRegistryUrl : env.NEXUS_REGISTRY_URL,
                                registryPath     : env.REGISTRY_PATH,
                                deployIp         : testTarget,
                                testCommand      : svc.test_command,
                                sshPort          : svc.ssh_port ?: env.GLOBAL_SSH_PORT?.toInteger() ?: 22
                            ])
                        } else if (doFlag(key) == '1' && !testTarget) {
                            echo "⏭️ [${svc.name}]: test hedef IP yok → test stage'i atlanıyor."
                        } else if (doFlag(key) == '1') {
                            echo "⏭️ [${svc.name}]: test komutu tanımlı değil → test stage'i atlanıyor."
                        } else {
                            echo "⏭️ [${svc.name}]: build edilmedi → test stage'i atlanıyor."
                        }
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    echo "Deploy Edilecek Uygulama: ${env.APP}:${env.VERSION}"
                    def servicesConfig = readYaml file: 'services.yml'
                    def credentialsMap = collectCredentials()
                    echo "📥 [Örnek] Tüm servis imajları önceden Nexus'tan çekiliyor..."
                    servicesConfig.services.each { svc ->
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        def targetsForPrePull = resolveTargets(svc)
                        def sshUserForService = svc.ssh_user ?: 'your-user'
                        def sshPortForService = svc.ssh_port ?: env.GLOBAL_SSH_PORT?.toInteger() ?: 22
                        targetsForPrePull.each { ip ->
                            pullService([
                                serviceName    : svc.name,
                                imageName      : imageName,
                                version        : env.VERSION,
                                deployIp       : ip,
                                nexusUrl       : env.NEXUS_URL,
                                nexusRegistryUrl: env.NEXUS_REGISTRY_URL,
                                registryPath   : env.REGISTRY_PATH,
                                sshUser        : sshUserForService,
                                sshPort        : sshPortForService
                            ])
                        }
                    }
                    def deployingSvcs = servicesConfig.services.findAll { s ->
                        def k = normalizeKey(s.name)
                        (doFlag(k) == '1') || env.PULL_EXISTING == '1'
                    }
                    def lastDeployingSvc = deployingSvcs ? deployingSvcs[-1] : null
                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        def shouldPull = (doFlag(key) == '1') || env.PULL_EXISTING == '1'
                        def sshPortForService = svc.ssh_port ?: env.GLOBAL_SSH_PORT?.toInteger() ?: 22
                        def targets = resolveTargets(svc)
                        targets.each { ip ->
                            if (shouldPull) {
                                def isFirstService          = (svc == servicesConfig.services[0])
                                def extraEnvVarsForService  = isFirstService ? credentialsMap : [:]
                                def envFileForService       = svc.env_file
                                def extraFilesForService    = svc.extra_files ?: []
                                deployService([
                                    serviceName             : imageName,
                                    imageName               : imageName,
                                    version                 : env.VERSION,
                                    appName                 : env.APP,
                                    deployIp                : ip,
                                    nexusUrl                : env.NEXUS_URL,
                                    nexusRegistryUrl        : env.NEXUS_REGISTRY_URL,
                                    registryPath            : env.REGISTRY_PATH,
                                    dockerComposeFile       : svc.docker_compose_file ?: 'docker-compose.yml',
                                    dockerComposeRemoteFile : svc.docker_compose_remote_file ?: svc.docker_compose_file ?: 'docker-compose.yml',
                                    envFile                 : envFileForService,
                                    extraEnvVars            : extraEnvVarsForService,
                                    extraFiles              : extraFilesForService,
                                    shouldPull              : false,
                                    sshUser                 : svc.ssh_user ?: 'your-user',
                                    sshPort                 : sshPortForService,
                                    runCompose              : (lastDeployingSvc != null && svc == lastDeployingSvc)
                                ])
                            } else {
                                echo "⏭️ [${svc.name} @ ${ip}]: değişmedi → pull atlanıyor."
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                sendEmailNotification([
                    status        : 'success',
                    appName       : env.APP,
                    recipientEmail: env.RECIPIENT_EMAIL,
                    nexusUrl      : env.NEXUS_URL
                ])
            }
        }
        failure {
            script {
                sendEmailNotification([
                    status        : 'failure',
                    appName       : env.APP,
                    recipientEmail: env.RECIPIENT_EMAIL,
                    nexusUrl      : env.NEXUS_URL
                ])
            }
        }
        aborted {
            script {
                sendEmailNotification([
                    status        : 'aborted',
                    appName       : env.APP,
                    recipientEmail: env.RECIPIENT_EMAIL,
                    nexusUrl      : env.NEXUS_URL
                ])
            }
        }
        always {
            cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)
        }
    }
}

// Helpers
def normalizeKey(String name) { name?.trim()?.toUpperCase() }
def resolveImageName(String name, String imageName) { imageName ?: name }
def setTagExists(String key, String value) { env."TAG_EXISTS_${key}" = value }
def tagExists(String key) { env."TAG_EXISTS_${key}" == '1' }
def setDoFlag(String key, String value) { env."DO_${key}" = value }
def doFlag(String key) { env."DO_${key}" ?: '0' }

// Öncelik sırası: pipeline parametresi DEPLOY_TARGETS → services.yml global deploy_targets
def resolveTargets(svc) {
    if (env.DEPLOY_TARGETS?.trim()) {
        return env.DEPLOY_TARGETS.split(',').collect { it.trim() }.findAll { it }
    }
    if (env.GLOBAL_TARGETS?.trim()) {
        return env.GLOBAL_TARGETS.split(',').collect { it.trim() }.findAll { it }
    }
    return []
}