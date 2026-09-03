// services.yml'deki tüm servisleri hedef IP'lere deploy eder: önce imajları önden çeker,
// sonra (yalnızca değişen) servisleri deploy eder. `docker compose up`ın her host'ta tam olarak
// bir kez çalışmasını, o host'a deploy eden SON servis üzerinden garanti eder.
//
// Kullanım (Jenkinsfile):
//   deployAllServices([
//       appName        : env.APP,
//       version        : env.VERSION,
//       nexusUrl       : CFG.NEXUS_URL,
//       registryPath   : CFG.REGISTRY_PATH,
//       credentialsMap : collectCredentials()
//   ])
def call(Map config) {
    def servicesFile = config.servicesFile ?: (env.SERVICES_FILE?.trim() ?: 'services.yml')
    def appName = config.appName
    def version = config.version
    def nexusUrl = config.nexusUrl
    def nexusRegistryUrl = config.nexusRegistryUrl ?: env.NEXUS_REGISTRY_URL ?: nexusUrl
    def registryPath = config.registryPath
    def nexusCredentialId = config.nexusCredentialId ?: env.NEXUS_CREDENTIAL_ID ?: 'Nexus_Credentials'
    def credentialsMap = config.credentialsMap ?: [:]
    // opsiyonel: deploy host'unda kullanılmayan TÜM imajları temizler (varsayılan kapalı)
    def pruneImages = config.pruneImages ?: false

    def servicesConfig = readYaml file: servicesFile

    def normalizeKey = { name -> name?.trim()?.toUpperCase() }
    def resolveImageName = { name, imageName -> imageName ?: name }
    // Öncelik: pipeline parametresi DEPLOY_TARGETS → services.yml servis bazlı deploy_targets →
    // Initialize stage'inde environments.<ortam>.deploy_targets'tan set edilen GLOBAL_TARGETS → IP1..IP4
    def resolveTargets = { svc ->
        if (env.DEPLOY_TARGETS?.trim()) {
            env.DEPLOY_TARGETS.split(',').collect { it.trim() }.findAll { it }
        } else if (svc?.deploy_targets) {
            svc.deploy_targets.collect { it.toString().trim() }.findAll { it }
        } else if (env.GLOBAL_TARGETS?.trim()) {
            env.GLOBAL_TARGETS.split(',').collect { it.trim() }.findAll { it }
        } else {
            [env.IP1, env.IP2, env.IP3, env.IP4].findAll { it?.trim() }
        }
    }
    def isDeployingSvc = { svc ->
        (env."DO_${normalizeKey(svc.name)}" ?: '0') == '1' || env.PULL_EXISTING == '1'
    }
    // Jenkinsfile'daki environment{} bloğundan (ortam bazlı) geliyorsa öncelik onda; yoksa
    // services.yml'deki servis alanı, o da yoksa varsayılan kullanılır (DEPLOY_TARGETS ile aynı mantık).
    def resolveComposeFile = { svc ->
        env.DOCKER_COMPOSE_FILE?.trim() ?: (svc.docker_compose_file ?: 'docker-compose.yml')
    }
    def resolveComposeRemoteFile = { svc ->
        env.DOCKER_COMPOSE_REMOTE_FILE?.trim() ?: (svc.docker_compose_remote_file ?: resolveComposeFile(svc))
    }
    def resolveSshUser = { svc ->
        svc.ssh_user ?: env.DEPLOY_SSH_USER?.trim() ?: config.sshUser ?: globalConfig().DEPLOY_SSH_USER ?: 'your-user'
    }
    // Initialize stage'i environments.<ortam>.ssh_port değerini GLOBAL_SSH_PORT'a yazar
    def resolveSshPort = { svc ->
        svc.ssh_port ?: (env.GLOBAL_SSH_PORT?.trim() ? env.GLOBAL_SSH_PORT.toInteger() : null) ?: config.sshPort ?: 22
    }

    // Yalnizca deploy EDILECEK servisler onden cekilir. Onceki surum tum servisleri cekiyordu:
    //  - degismemis servisin imaji bosuna indiriliyordu (ustelik pullService once `docker rmi` ile
    //    dogru duran yerel kopyayi siliyor, yani her seferinde bastan cekiyordu),
    //  - pullService hata durumunda `error` attigi icin, o VERSION icin hic push edilmemis bir
    //    servis tum deploy'u dusuruyordu.
    echo "📥 Deploy edilecek servis imajları önceden Nexus'tan çekiliyor..."
    servicesConfig.services.each { svc ->
        if (!isDeployingSvc(svc)) {
            echo "⏭️ [${svc.name}]: değişmedi → ön çekim atlanıyor."
            return
        }
        def imageName = resolveImageName(svc.name, svc.image_name)
        resolveTargets(svc).each { ip ->
            pullService([
                serviceName      : svc.name,
                imageName        : imageName,
                version          : version,
                deployIp         : ip,
                nexusUrl         : nexusUrl,
                nexusRegistryUrl : nexusRegistryUrl,
                registryPath     : registryPath,
                nexusCredentialId: nexusCredentialId,
                sshUser          : resolveSshUser(svc),
                sshPort          : resolveSshPort(svc)
            ])
        }
    }

    // Her IP için o IP'ye deploy olan SON servisi bul; compose up her host'ta tam olarak bir kez
    // çalışsın. (Servisler farklı deploy_targets kullanabildiği için tek bir global "son servis"
    // yeterli değil.)
    def lastDeployingSvcByIp = [:]
    servicesConfig.services.each { svc ->
        if (!isDeployingSvc(svc)) return
        resolveTargets(svc).each { ip -> lastDeployingSvcByIp[ip] = svc }
    }

    servicesConfig.services.each { svc ->
        def imageName = resolveImageName(svc.name, svc.image_name)
        def shouldDeploy = isDeployingSvc(svc)
        resolveTargets(svc).each { ip ->
            if (!shouldDeploy) {
                echo "⏭️ [${svc.name} @ ${ip}]: değişmedi → deploy atlanıyor."
                return
            }
            // credentialsMap her deploy edilen servise geçilir (sadece "ilk" servise değil) —
            // aksi halde ilk servis dışındaki bir servis değiştiğinde .env credential güncellemesi
            // hiç uygulanmaz (hepsi aynı appName'in paylaştığı .env'e yazar).
            deployService([
                serviceName            : imageName,
                imageName              : imageName,
                version                : version,
                appName                : appName,
                deployIp               : ip,
                nexusUrl               : nexusUrl,
                nexusRegistryUrl       : nexusRegistryUrl,
                registryPath           : registryPath,
                dockerComposeFile      : resolveComposeFile(svc),
                dockerComposeRemoteFile: resolveComposeRemoteFile(svc),
                envFile                : svc.env_file,
                extraEnvVars           : credentialsMap,
                extraFiles             : svc.extra_files ?: [],
                shouldPull             : false,
                sshUser                : resolveSshUser(svc),
                sshPort                : resolveSshPort(svc),
                runCompose             : (lastDeployingSvcByIp[ip] == svc),
                pruneImages            : pruneImages,
                composeWait            : (config.composeWait != null) ? config.composeWait : true,
                composeWaitTimeout     : config.composeWaitTimeout ?: 180,
                pruneDangling          : (config.pruneDangling != null) ? config.pruneDangling : true
            ])
        }
    }
}
