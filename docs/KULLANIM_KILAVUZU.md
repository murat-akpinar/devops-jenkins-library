# Jenkins Shared Library - Kullanım Kılavuzu

> For English documentation: **[USAGE_GUIDE.md](./USAGE_GUIDE.md)**

Bu kılavuz, Jenkins Shared Library'yi yeni bir projede nasıl kullanacağınızı **adım adım** açıklar.

## İçindekiler

- [Önkoşullar](#önkoşullar)
- [1. Adım: services.yml Oluşturma](#1-adım-servicesyml-oluşturma)
- [2. Adım: Jenkinsfile Oluşturma](#2-adım-jenkinsfile-oluşturma)
- [3. Adım: sonar-project.properties Oluşturma](#3-adım-sonar-projectproperties-oluşturma)
- [4. Adım: Docker Compose Dosyası](#4-adım-docker-compose-dosyası)
- [5. Adım: Jenkins'te Pipeline Job Oluşturma](#5-adım-jenkinste-pipeline-job-oluşturma)
- [6. Adım: İlk Build](#6-adım-ilk-build)
- [Örnek Senaryolar](#örnek-senaryolar)
- [Projeye Özel Ayarlar](#projeye-özel-ayarlar)

---

## Önkoşullar

CI/CD pipeline kurulumuna başlamadan önce aşağıdakilerin hazır olduğundan emin olun:

1. **CI/CD Pipeline Öncesi Hazırlık** — `01-cicd-hazirliklari.md` dosyasındaki tüm adımlar tamamlanmış olmalı
2. **Jenkins'te Global Library Tanımlı** — `devops-jenkins-library` tanımlı olmalı
3. **Pipeline Utility Steps Plugin Yüklü** — YAML okuma için gerekli
4. **Projede Git Repository Var** — Kod Git'te olmalı

**[CI/CD Pipeline Öncesi Hazırlık Rehberi →](./01-cicd-hazirliklari.md)**

---

## 1. Adım: services.yml Oluşturma

Projenizin **root dizininde** `services.yml` dosyası oluşturun. Bu dosya uygulama adını, ortam yapılandırmasını ve servis listesini tanımlar.

### Temel services.yml Yapısı

```yaml
app_name: my-project                   # Uygulama adı — deploy path: /app/my-project/
recipients: 'Takim_1, Takim_2'         # E-posta alıcıları (emailTeams anahtarı veya direkt adres)

environments:                          # Ortam başına deploy hedefleri
  test:
    deploy_targets:
      - 192.168.1.10
  preprod:
    deploy_targets:
      - 192.168.2.10
  prod:
    deploy_targets:
      - 192.168.3.10

services:
  - name: backend                      # Servis adı (zorunlu)
    path: backend/                     # Servis dizini — git diff için (zorunlu)
    dockerfile: Dockerfile             # Dockerfile yolu (zorunlu)
    image_name: myapp_backend          # Docker imaj adı (zorunlu)
    env_file: backend/.env             # .env dosyası yolu (opsiyonel)
    docker_compose_file: docker-compose.yml
    extra_files:
      - { src: "default.conf", dest: "/app/my-project/default.conf" }
    test_command: "npm test"           # Test komutu (opsiyonel)

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend
    env_file: frontend/.env
```

> **Gerçek Örnek:** `Example/services_example.yml` dosyasına bakın.

### Alan Açıklamaları

| Alan | Zorunlu | Açıklama |
|------|---------|----------|
| `app_name` | ✅ | Uygulama adı; deploy path `/app/<app_name>/` olarak belirlenir |
| `recipients` | ❌ | Bildirim e-postaları (`emailTeams` anahtarı veya doğrudan adres) |
| `environments` | ✅ | Ortam başına `deploy_targets` IP listesi |
| `services[].name` | ✅ | Servis adı |
| `services[].path` | ✅ | Servis dizini (git diff için) |
| `services[].dockerfile` | ✅ | Dockerfile yolu |
| `services[].image_name` | ✅ | Docker imaj adı |
| `services[].env_file` | ❌ | .env dosyası yolu (ilk servis için Git'ten kopyalanır) |
| `services[].docker_compose_file` | ❌ | Compose dosyası yolu |
| `services[].docker_compose_remote_file` | ❌ | Sunucuda hangi adla saklanacağı |
| `services[].extra_files` | ❌ | Ek dosya/dizin listesi |
| `services[].ssh_user` | ❌ | SSH kullanıcı adı |
| `services[].test_command` | ❌ | Test komutu |

### extra_files Detaylı Açıklama

```yaml
extra_files:
  - default.conf              # String → /app/myapp/default.conf
  - nginx/nginx.conf          # String → /app/myapp/nginx.conf (basename ile)
  - backend                   # String → /app/myapp/backend/ (dizin)
  - src: webrtc/config.env    # Map formatı — özel hedef path için
    dest: /app/myapp/config.env
```

- Kaynak dosya mı dizin mi otomatik tespit edilir
- Sunucuda hedef path farklı türde varsa otomatik temizlenir (Docker mount hatalarını önler)
- `.env` dosyasından **önce** kopyalanır

---

## 2. Adım: Jenkinsfile Oluşturma

Projenizin **root dizininde** Jenkinsfile oluşturun. Tam örnek `Example/Jenkins_example.groovy` dosyasındadır.

### Temel Jenkinsfile Yapısı

```groovy
@Library('devops-jenkins-library') _

pipeline {
    agent { label "linux" }

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['test', 'preprod', 'prod'], description: 'Deploy ortamını seçin')
        booleanParam(name: 'FORCE_REBUILD', defaultValue: false, description: 'Tüm imajları zorla yeniden build et')
        booleanParam(name: 'USE_EXISTING_IMAGE', defaultValue: false, description: 'Tag mevcutsa build/push atla, mevcut imajı kullan')
        string(name: 'VERSION_TAG', defaultValue: 'test-v1.0', description: 'Deploy edilecek imaj tag')
    }

    environment {
        VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"
        // Credentials (otomatik .env dosyasına yazılır)
        // PSQL_HOST = credentials('psql_myproject_host')
        // PSQL_PASS = credentials('psql_myproject_pass')
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
                    env.NEXUS_CREDENTIAL_ID  = CFG.NEXUS_CREDENTIAL_ID ?: 'Nexus_Credentials'
                    env.HARBOR_CREDENTIAL_ID = CFG.HARBOR_CREDENTIAL_ID ?: ''
                    def harborHost = CFG.HARBOR_URL?.trim() ? CFG.HARBOR_URL.replaceAll('^https?://', '') : ''
                    def harborPath = CFG.HARBOR_REGISTRY_PATH?.trim() ?: ''
                    env.HARBOR_REGISTRY = (harborHost && harborPath) ? "${harborHost}/${harborPath}" : ''

                    if (!fileExists('services.yml')) {
                        error "❌ services.yml dosyası bulunamadı!"
                    }
                    def servicesConfig = readYaml file: 'services.yml'
                    env.APP             = servicesConfig.app_name ?: error("❌ services.yml içinde 'app_name' tanımlı değil!")
                    env.RECIPIENT_EMAIL = servicesConfig.recipients ?: ''
                    def envConfig = servicesConfig.environments?."${params.ENVIRONMENT}"
                    if (!envConfig) error "❌ '${params.ENVIRONMENT}' ortamı services.yml içinde tanımlı değil!"
                    env.GLOBAL_TARGETS = envConfig.deploy_targets
                        ? envConfig.deploy_targets.collect { it.toString().trim() }.join(',')
                        : ''
                    echo "ℹ️ VERSION: ${env.VERSION} | Ortam: ${params.ENVIRONMENT} | APP: ${env.APP}"
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
                    } else {
                        servicesConfig.services.each { svc ->
                            def key = normalizeKey(svc.name)
                            def exists = tagExists(key)
                            if (force || !exists || !hasBaseline) {
                                setDoFlag(key, '1')
                            } else {
                                def changed = checkDiff(svc.path, prev, curr)
                                setDoFlag(key, (changed == 1) ? '1' : '0')
                            }
                            echo "  - ${svc.name}: ${doFlag(key) == '1' ? 'Build yapılacak' : 'Atlanacak'}"
                        }
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

        stage('DockerScan Scan')         { steps { trivyScan(env.VERSION) } }
        stage('DockerScan Quality Gate') { steps { trivyQualityGate(env.APP, 'C') } }

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
                                testCommand      : svc.test_command
                            ])
                        } else {
                            echo "⏭️ [${svc.name}]: test atlanıyor."
                        }
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    def servicesConfig = readYaml file: 'services.yml'
                    def credentialsMap = collectCredentials()

                    // Önce tüm imajları pull et
                    servicesConfig.services.each { svc ->
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        resolveTargets(null).each { ip ->
                            pullService([
                                serviceName      : svc.name,
                                imageName        : imageName,
                                version          : env.VERSION,
                                deployIp         : ip,
                                nexusUrl         : env.NEXUS_URL,
                                nexusRegistryUrl : env.NEXUS_REGISTRY_URL,
                                registryPath     : env.REGISTRY_PATH,
                                sshUser          : svc.ssh_user ?: 'your-user'
                            ])
                        }
                    }

                    // Deploy işlemi (son servis için docker compose up)
                    def deployingSvcs = servicesConfig.services.findAll { s ->
                        (doFlag(normalizeKey(s.name)) == '1') || env.PULL_EXISTING == '1'
                    }
                    def lastDeployingSvc = deployingSvcs ? deployingSvcs[-1] : null

                    servicesConfig.services.each { svc ->
                        def key = normalizeKey(svc.name)
                        def imageName = resolveImageName(svc.name, svc.image_name)
                        def shouldDeploy = (doFlag(key) == '1') || env.PULL_EXISTING == '1'
                        if (shouldDeploy) {
                            resolveTargets(null).each { ip ->
                                deployService([
                                    serviceName             : imageName,
                                    version                 : env.VERSION,
                                    appName                 : env.APP,
                                    deployIp                : ip,
                                    nexusUrl                : env.NEXUS_URL,
                                    nexusRegistryUrl        : env.NEXUS_REGISTRY_URL,
                                    registryPath            : env.REGISTRY_PATH,
                                    dockerComposeFile       : svc.docker_compose_file ?: 'docker-compose.yml',
                                    dockerComposeRemoteFile : svc.docker_compose_remote_file ?: svc.docker_compose_file ?: 'docker-compose.yml',
                                    envFile                 : svc.env_file,
                                    extraEnvVars            : (svc == servicesConfig.services[0]) ? credentialsMap : [:],
                                    extraFiles              : svc.extra_files ?: [],
                                    shouldPull              : false,
                                    sshUser                 : svc.ssh_user ?: 'your-user',
                                    runCompose              : (lastDeployingSvc != null && svc == lastDeployingSvc)
                                ])
                            }
                        } else {
                            echo "⏭️ [${svc.name}]: değişmedi → deploy atlanıyor."
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                sendEmailNotification([status: 'success', appName: env.APP, recipientEmail: env.RECIPIENT_EMAIL, nexusUrl: env.NEXUS_URL])
            }
        }
        failure {
            script {
                sendEmailNotification([status: 'failure', appName: env.APP, recipientEmail: env.RECIPIENT_EMAIL, nexusUrl: env.NEXUS_URL])
            }
        }
        aborted {
            script {
                sendEmailNotification([status: 'aborted', appName: env.APP, recipientEmail: env.RECIPIENT_EMAIL, nexusUrl: env.NEXUS_URL])
            }
        }
        always {
            cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)
        }
    }
}

// Helper fonksiyonlar — Jenkinsfile'ın sonuna ekleyin
def normalizeKey(String name) { name?.trim()?.toUpperCase() }
def resolveImageName(String name, String imageName) { imageName ?: name }
def setTagExists(String key, String value) { env."TAG_EXISTS_${key}" = value }
def tagExists(String key) { env."TAG_EXISTS_${key}" == '1' }
def setDoFlag(String key, String value) { env."DO_${key}" = value }
def doFlag(String key) { env."DO_${key}" ?: '0' }

// Öncelik: DEPLOY_TARGETS env var → services.yml environments → boş liste
def resolveTargets(svc) {
    if (env.DEPLOY_TARGETS?.trim()) {
        return env.DEPLOY_TARGETS.split(',').collect { it.trim() }.findAll { it }
    }
    if (env.GLOBAL_TARGETS?.trim()) {
        return env.GLOBAL_TARGETS.split(',').collect { it.trim() }.findAll { it }
    }
    return []
}
```

> **Tam Örnek:** `Example/Jenkins_example.groovy` dosyasına bakın.

---

## 3. Adım: sonar-project.properties Oluşturma

Projenizin **root dizininde** `sonar-project.properties` dosyası oluşturun:

```properties
sonar.projectKey=my-project-key
sonar.projectName=My Project Name
sonar.projectVersion=1.0
sonar.sourceEncoding=UTF-8
sonar.sources=backend/src
sonar.tests=backend/tests
sonar.exclusions=**/__pycache__/**,**/node_modules/**
```

---

## 4. Adım: Docker Compose Dosyası

Proje root dizininde Docker Compose dosyası oluşturun. `.env` dosyasındaki `VERSION` değişkeni otomatik okunur:

```yaml
services:
  backend:
    image: myapp_backend:${VERSION}
    volumes:
      - type: bind
        source: /app/my-project/default.conf
        target: /etc/nginx/conf.d/default.conf
    environment:
      - VERSION=${VERSION}

  frontend:
    image: myapp_frontend:${VERSION}
```

> **Not:** Docker Compose varsayılan olarak aynı dizindeki `.env` dosyasını okur. `--env-file` parametresi gerekmez.

---

## 5. Adım: Jenkins'te Pipeline Job Oluşturma

1. Jenkins → **New Item**
2. İsim verin (örn: `my-project-pipeline`)
3. **Pipeline** seçin ve **OK** tıklayın
4. **Pipeline** sekmesinde:
   - **Definition**: Pipeline script from SCM
   - **SCM**: Git
   - **Repository URL**: Projenizin Git URL'i
   - **Credentials**: Gerekirse repo erişim bilgileri
   - **Branch Specifier**: `*/main`
   - **Script Path**: `Jenkinsfile` (veya `Jenkinsfile_test.groovy`)
5. **Save** tıklayın

---

## 6. Adım: İlk Build

1. Pipeline job sayfasında **Build with Parameters** butonuna tıklayın
2. Parametreleri ayarlayın:
   - **ENVIRONMENT**: `test`
   - **VERSION_TAG**: `test-v1.0`
   - **FORCE_REBUILD**: `true` (ilk build için)
   - **USE_EXISTING_IMAGE**: `false`
3. **Build** butonuna tıklayın

---

## Örnek Senaryolar

### Senaryo 1: Tek Servis

```yaml
app_name: my-app
recipients: 'dev@company.com'
environments:
  test:
    deploy_targets:
      - 192.168.1.10

services:
  - name: backend
    path: backend/
    dockerfile: Dockerfile
    image_name: myapp_backend
    env_file: backend/.env
    test_command: "npm test"
```

### Senaryo 2: Backend + Frontend

```yaml
app_name: my-app
recipients: 'Takim_1'
environments:
  test:
    deploy_targets:
      - 192.168.1.10
  prod:
    deploy_targets:
      - 192.168.3.10

services:
  - name: backend
    path: backend/
    dockerfile: Dockerfile
    image_name: myapp_backend
    env_file: .env

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend
```

### Senaryo 3: Backend + Frontend + Nginx (extra_files ile)

```yaml
app_name: my-app
recipients: 'Takim_1, Takim_2'
environments:
  test:
    deploy_targets:
      - 192.168.1.10

services:
  - name: backend
    path: backend/
    dockerfile: Dockerfile
    image_name: myapp_backend
    env_file: .env
    extra_files:
      - { src: "default.conf", dest: "/app/my-app/default.conf" }

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend

  - name: nginx
    path: nginx/
    dockerfile: Dockerfile
    image_name: myapp_nginx
    extra_files:
      - nginx/nginx.conf
```

---

## Projeye Özel Ayarlar

### Credentials Tanımlama

Jenkins'te tanımlı credentials'ları `environment` bloğunda kullanın:

```groovy
environment {
    VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"

    // Credentials — otomatik olarak .env dosyasına yazılır
    PSQL_HOST = credentials('psql_myproject_host')
    PSQL_PASS = credentials('psql_myproject_pass')
    JWT_SECRET_KEY = credentials('jwt_myproject_secret')
}
```

`deployService()` çağrısında `extraEnvVars: collectCredentials()` ile tüm credentials otomatik toplanır ve `.env` dosyasına yazılır.

### E-posta Alıcılarını Güncelleme

`emailTeams.groovy` dosyasına takım e-posta listelerini ekleyin:

```groovy
def call() {
    return [
        'Takim_1': 'dev1@company.com, dev2@company.com',
        'Takim_2': 'ops@company.com',
    ]
}
```

`services.yml`'de `recipients: 'Takim_1, Takim_2'` ile kullanılır.

### globalConfig.groovy Güncelleme

`vars/globalConfig.groovy` dosyasını kendi ortamınıza göre doldurun. Trivy ayarlarının çalışması için Trivy host'unda **[DockerScan](https://github.com/murat-akpinar/DockerScan)** kurulu olması gerekir.

```groovy
def call() {
    return [
        NEXUS_URL           : 'http://192.168.1.100:8081',
        NEXUS_REGISTRY_URL  : 'http://192.168.1.100:8090',
        REGISTRY_PATH       : 'my-registry',
        NEXUS_CREDENTIAL_ID : 'Nexus_Credentials',
        HARBOR_URL          : '',   // Harbor kullanılmıyorsa boş bırakın
        HARBOR_REGISTRY_PATH: '',
        HARBOR_CREDENTIAL_ID: '',
        SONAR_SERVER        : 'my-sonar-server',
        DOCKERSCAN_HOST              : '192.168.1.200',
        DOCKERSCAN_SSH_USER          : 'jenkins',
        DOCKERSCAN_SCRIPT_PATH       : '/app/DockerScan/trigger-nexus.sh',  // DockerScan: https://github.com/murat-akpinar/DockerScan
        DOCKERSCAN_BACKEND_PORT : '3018',
    ]
}
```

---

## Önemli Notlar

1. **Helper Fonksiyonlar:** Jenkinsfile'ın sonuna helper fonksiyonları eklemeyi unutmayın
2. **services.yml:** `app_name` ve `environments` alanları zorunludur
3. **SonarQube:** Her projede `sonar-project.properties` dosyası olmalıdır
4. **SSH Erişimi:** Deploy sunucusuna SSH erişimi kurulu olmalıdır (`01-cicd-hazirliklari.md`)
5. **pullService:** Compose çalıştırılmadan önce ayrıca çağrılır; `deployService` içinde `shouldPull: false` olarak kullanılır

---

## Sorun Giderme

**services.yml bulunamadı:** Dosyanın proje root dizininde olduğundan ve Git'e commit edildiğinden emin olun.

**Shared library bulunamadı:** Jenkins → Manage Jenkins → Configure System → Global Pipeline Libraries → `devops-jenkins-library` tanımlı mı kontrol edin.

**extra_files kopyalanmıyor:** Log'larda `📁 extra file/dizin kopyalanacak...` mesajını ve kaynak dosyanın projede mevcut olduğunu kontrol edin.

Detaylı sorun giderme için: **[SSS.md](./SSS.md)**

---

## Daha Fazla Bilgi

- **Fonksiyon Referansları:** [../README.md](../README.md)
- **Sık Sorulan Sorular:** [SSS.md](./SSS.md)
- **Gerçek Örnekler:** [../Example/](../Example/)
- **CI/CD Öncesi Hazırlık:** [01-cicd-hazirliklari.md](./01-cicd-hazirliklari.md)
