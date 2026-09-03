[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-1a1a1a?style=flat-square&labelColor=1a1a1a&color=8a6f3a)](LICENSE)
[![Built with Claude Code](https://img.shields.io/badge/built%20with-Claude%20Code-1a1a1a?style=flat-square&labelColor=1a1a1a&color=d8b66b)](https://claude.ai/claude-code)
[![Status](https://img.shields.io/badge/status-stable-1a1a1a?style=flat-square&labelColor=1a1a1a&color=2d7a2d)](https://github.com/murat-akpinar/devops-jenkins-library)
[![Jenkins](https://img.shields.io/badge/Jenkins-Shared%20Library-1a1a1a?style=flat-square&labelColor=1a1a1a&color=D33833&logo=jenkins&logoColor=fff)](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)
[![Groovy](https://img.shields.io/badge/Groovy-4.x-1a1a1a?style=flat-square&labelColor=1a1a1a&color=4298B8&logo=apachegroovy&logoColor=fff)](https://groovy-lang.org)
[![Docker](https://img.shields.io/badge/Docker-compose-1a1a1a?style=flat-square&labelColor=1a1a1a&color=2496ED&logo=docker&logoColor=fff)](https://www.docker.com)
[![SonarQube](https://img.shields.io/badge/SonarQube-analysis-1a1a1a?style=flat-square&labelColor=1a1a1a&color=4E9BCD&logo=sonarqube&logoColor=fff)](https://www.sonarsource.com/products/sonarqube/)
[![DockerScan](https://img.shields.io/badge/DockerScan-security%20scan-1a1a1a?style=flat-square&labelColor=1a1a1a&color=1904DA)](https://trivy.dev)
[![Nexus](https://img.shields.io/badge/Nexus-registry-1a1a1a?style=flat-square&labelColor=1a1a1a&color=1B75BB)](https://www.sonatype.com/products/sonatype-nexus-repository)

# Jenkins Shared Library

> For English documentation: **[README.md](README.md)**

Jenkins CI/CD pipeline'larında kullanılan ortak fonksiyonlar ve yapılandırmaları içeren shared library.

## İçindekiler

- [Hızlı Başlangıç](#hızlı-başlangıç)
- [Dizin Yapısı](#dizin-yapısı)
- [Kurulum](#kurulum)
- [Kullanım](#kullanım)
- [Fonksiyonlar](#fonksiyonlar)
- [services.yml Yapılandırması](#servicesyml-yapılandırması)
- [Multi-Server Deployment](#multi-server-deployment)
- [Sorun Giderme](#sorun-giderme)

---

## Hızlı Başlangıç

### CI/CD Pipeline Öncesi Hazırlık

CI/CD pipeline kurulumundan önce yapılması gereken hazırlık adımları için **mutlaka** `docs/01-cicd-hazirliklari.md` dosyasını okuyun:

- Network izinleri talep etme
- Docker insecure registry ayarları
- Nexus bağlantı yapılandırması
- SSH anahtarı kurulumu

**[CI/CD Pipeline Öncesi Hazırlık Rehberi →](docs/01-cicd-hazirliklari.md)**

### Yeni Projede Kullanım

1. **Hazırlık adımlarını tamamlayın** (`docs/01-cicd-hazirliklari.md`)
2. **Jenkinsfile oluşturun** (örnek: `Example/Jenkins_example.groovy`)
3. **services.yml oluşturun** (örnek: `Example/services_example.yml`)
4. **Jenkins'te pipeline job oluşturun**

Detaylı adımlar için: **[Kullanım Kılavuzu →](docs/KULLANIM_KILAVUZU.md)**

---

## Dizin Yapısı

```
devops-jenkins-library/
├── vars/                              # Pipeline fonksiyonları (Groovy)
│   ├── globalConfig.groovy            # Ortak yapılandırma (Nexus, Harbor, Trivy, SonarQube)
│   ├── collectCredentials.groovy      # Credentials toplama
│   ├── sonarQubeAnalysis.groovy       # SonarQube analizi
│   ├── sonarQubeQualityGate.groovy    # SonarQube Quality Gate
│   ├── checkTagOnNexus.groovy         # Nexus tag kontrolü
│   ├── checkDiff.groovy               # Git diff kontrolü
│   ├── buildAndPushService.groovy     # Docker build & push
│   ├── pullService.groovy             # Sunucuya Docker image pull
│   ├── runTestsInContainer.groovy     # Container'da test çalıştırma
│   ├── deployService.groovy           # Deploy işlemi (tek servis)
│   ├── deployAllServices.groovy       # services.yml'deki tüm servisleri deploy eder
│   ├── dockerCleanup.groovy           # Agent'ta güvenli Docker temizliği
│   ├── trivyScan.groovy               # DockerScan güvenlik taraması
│   ├── trivyQualityGate.groovy        # DockerScan Quality Gate kontrolü
│   ├── checkovScan.groovy             # Checkov IaC güvenlik taraması
│   ├── checkovQualityGate.groovy      # Checkov Quality Gate kontrolü
│   ├── osvScan.groovy                 # OSV bağımlılık güvenlik açığı taraması
│   ├── osvQualityGate.groovy          # OSV Quality Gate kontrolü
│   ├── sendEmailNotification.groovy   # E-posta bildirimi
│   └── emailTeams.groovy              # Takım e-posta listesi
├── Example/                           # Örnek dosyalar
│   ├── Jenkins_example.groovy         # Tam Jenkinsfile örneği
│   ├── services_example.yml          # services.yml örneği
│   └── sonar-project_example.properties # sonar-project.properties örneği
├── docs/                              # Belgeler
│   ├── 01-cicd-hazirliklari.md       # CI/CD öncesi hazırlık rehberi
│   ├── 02-pipeline-kurulumu.md       # Pipeline kurulum adımları
│   ├── 03-jenkins-plugin-kurulumu.md # Gerekli Jenkins eklentileri
│   ├── KULLANIM_KILAVUZU.md          # Detaylı kullanım kılavuzu
│   └── SSS.md                        # Sık sorulan sorular
├── credentials_test.groovy.example    # Şablon: test ortamı credential'larını toplu yükler
├── credentials_prod.groovy.example    # Şablon: prod ortamı credential'larını toplu yükler
└── README.md                          # İngilizce dokümantasyon
```

---

## Kurulum

### 1. Jenkins'te Global Library Tanımlama

1. **Manage Jenkins** → **Configure System** → **Global Pipeline Libraries**
2. **Global Untrusted Pipeline Libraries** altında **Add** butonuna tıklayın
3. Aşağıdaki bilgileri girin:
   - **Name**: `devops-jenkins-library`
   - **Default version**: `main`
   - **Retrieval method**: **Modern SCM**
   - **Source Code Management**: **Git**
   - **Project Repository**: Bu repo'nun URL'i
   - **Credentials**: Gerekirse repo erişim bilgileri

### 2. Jenkins Credentials Tanımlama

**Manage Jenkins** → **Credentials** → **System** → **Global credentials** → **Add Credentials**

| ID | Tür | Açıklama |
|----|-----|----------|
| `Nexus_Credentials` | Username with password | Nexus Docker registry kullanıcı adı ve şifresi |
| `Harbor_Credentials` | Username with password | Harbor registry (opsiyonel, kullanılmıyorsa boş bırakın) |

> **Not:** ID'ler büyük/küçük harfe duyarlıdır. `globalConfig.groovy` dosyasındaki `NEXUS_CREDENTIAL_ID` değeriyle eşleşmelidir.

### 3. Gerekli Jenkins Eklentileri

Aşağıdaki eklentilerin Jenkins'te kurulu olması gerekmektedir:

| Eklenti | Plugin ID | Neden Gerekli |
|---------|-----------|---------------|
| Pipeline (Workflow Aggregator) | `workflow-aggregator` | Declarative Pipeline ve `@Library` desteği |
| Git | `git` | Shared library'yi Git'ten çekmek için |
| Pipeline Utility Steps | `pipeline-utility-steps` | `readYaml`, `readFile`, `writeFile`, `fileExists` |
| SonarQube Scanner | `sonar` | `withSonarQubeEnv()`, `waitForQualityGate()` |
| Email Extension | `email-ext` | HTML e-posta bildirimleri (`emailext`) |
| Credentials Binding | `credentials-binding` | `withCredentials()`, `usernamePassword()` |
| Credentials | `credentials` | Nexus/Harbor credential yönetimi |

Kurulum adımları ve sorun giderme için: **[docs/03-jenkins-plugin-kurulumu.md](docs/03-jenkins-plugin-kurulumu.md)**

---

## Kullanım

### Temel Kullanım

```groovy
@Library('devops-jenkins-library') _

pipeline {
    agent { label "linux" }

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['test', 'preprod', 'prod'], description: 'Deploy ortamını seçin')
        booleanParam(name: 'FORCE_REBUILD', defaultValue: false, description: 'Tüm imajları zorla yeniden build et')
        string(name: 'VERSION_TAG', defaultValue: 'test-v1.0', description: 'Deploy edilecek imaj tag')
    }

    environment {
        VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    def CFG = globalConfig()
                    env.NEXUS_URL           = CFG.NEXUS_URL
                    env.NEXUS_REGISTRY_URL  = CFG.NEXUS_REGISTRY_URL ?: CFG.NEXUS_URL
                    env.REGISTRY_PATH       = CFG.REGISTRY_PATH
                    env.SONAR_SERVER        = CFG.SONAR_SERVER
                    env.NEXUS_CREDENTIAL_ID = CFG.NEXUS_CREDENTIAL_ID ?: 'Nexus_Credentials'

                    def servicesConfig = readYaml file: 'services.yml'
                    env.APP            = servicesConfig.app_name
                    env.RECIPIENT_EMAIL = servicesConfig.recipients ?: ''
                    def envConfig = servicesConfig.environments?."${params.ENVIRONMENT}"
                    env.GLOBAL_TARGETS = envConfig?.deploy_targets
                        ? envConfig.deploy_targets.collect { it.toString().trim() }.join(',')
                        : ''
                }
            }
        }
        // Diğer stage'ler...
    }
}
```

Tam örnek için: **[Example/Jenkins_example.groovy](Example/Jenkins_example.groovy)**

---

## Fonksiyonlar

### `globalConfig()`

Ortak yapılandırmayı (Nexus, Harbor, SonarQube, Trivy) döndürür. `globalConfig.groovy` dosyasını kendi ortamınıza göre düzenleyin.

**Dönen Değer:**
```groovy
[
    // Nexus REST API (tag kontrol için)
    NEXUS_URL           : "http://YOUR_NEXUS_IP:8081",
    // Nexus Docker registry (push/pull için)
    NEXUS_REGISTRY_URL  : "http://YOUR_NEXUS_IP:8090",
    REGISTRY_PATH       : "your-registry",
    NEXUS_CREDENTIAL_ID : "Nexus_Credentials",

    // Harbor (opsiyonel — kullanılmıyorsa boş bırakın)
    HARBOR_URL          : "",
    HARBOR_REGISTRY_PATH: "",
    HARBOR_CREDENTIAL_ID: "",

    // SonarQube
    SONAR_SERVER        : "your-sonar-server",

    // Trivy — DockerScan: https://github.com/murat-akpinar/DockerScan
    DOCKERSCAN_HOST              : "YOUR_DOCKERSCAN_HOST_IP",
    DOCKERSCAN_SSH_USER          : "your-user",
    DOCKERSCAN_SCRIPT_PATH       : "/app/DockerScan/trigger-nexus.sh",
    DOCKERSCAN_BACKEND_PORT      : "3018",

    // Checkov — IaC güvenlik taraması
    CHECKOV_ENABLED     : true,                // false → adımı atla
    CHECKOV_SOFT_FAIL   : true,                // true → hata olsa pipeline devam eder
    CHECKOV_SCRIPT_PATH : "/app/DockScan/trigger-checkov.sh",

    // OSV-Scanner — bağımlılık güvenlik açığı taraması
    OSV_ENABLED         : true,                // false → adımı atla
    OSV_SOFT_FAIL       : true,                // true → hata olsa pipeline devam eder
    OSV_SCRIPT_PATH     : "/app/DockScan/trigger-osv.sh",
]
```

---

### `collectCredentials()`

Pipeline `environment` bloğundaki credentials'ları otomatik olarak toplar.

**Dönen Değer:** `Map<String, String>`

**Özellikler:**
- Sistem değişkenlerini otomatik filtreler (`JENKINS_*`, `BUILD_*`, `DO_*`, `GIT_*` vb.)
- Manuel liste gerekmez

**Kullanım:**
```groovy
def credentialsMap = collectCredentials()
deployService([
    // ...
    extraEnvVars: credentialsMap
])
```

---

### `sonarQubeAnalysis(serverName, options)`

SonarQube kod analizi yapar. Akış, workspace içeriğine göre otomatik seçilir:

- **`.sln` bulunursa → .NET akışı.** `dotnet sonarscanner begin` / `dotnet build` / `end` çalıştırılır. Repoda birden fazla `.sln` varsa manuel seçim gerekmez, hepsi tek analiz içinde build edilir. Proje anahtarı/adı ve exclusion değerleri `sonar-project.properties`'ten okunur; SonarScanner for .NET (v8+) bu dosyayı tanımadığı ve workspace'te görürse `end` adımında hata verdiği için, dosya analiz süresince geçici olarak kaldırılıp sonra geri konur. Tanımlı olmayan alanlar hiç geçilmez — boş bir `/d:` değeri "format geçersiz" uyarısına yol açıyor.
- **`.sln` yoksa (PHP, Node, Python, …) → generic akış.** Düz `sonar-scanner` çalıştırılır; `sonar-project.properties` dosyasını kendisi okur.

**Parametreler:**
- `serverName` (String): Jenkins'te tanımlı SonarQube server adı
- `options` (Map, opsiyonel): `dotnetRoot` — .NET SDK kök dizini (varsayılan: `env.DOTNET_ROOT` ya da `/home/jenkins/.dotnet`)

> Proje root dizininde `sonar-project.properties` dosyası olmalı. Örnek için: `Example/sonar-project_example.properties`.

```groovy
sonarQubeAnalysis(env.SONAR_SERVER)
sonarQubeAnalysis(env.SONAR_SERVER, [dotnetRoot: '/opt/dotnet'])
```

---

### `sonarQubeQualityGate(config)`

SonarQube Quality Gate sonucunu kontrol eder.

**Parametreler (Map, opsiyonel):**
- `timeoutMinutes` — sonuç beklerken üst sınır (varsayılan: `10`). Webhook hiç gelmezse
  adım bu süre sonunda hata verir; önceden süresiz bekleyip bir executor'ı tutuyordu.

```groovy
sonarQubeQualityGate()
```

---

### `checkTagOnNexus(serviceName, version, nexusUrl, registryPath[, credentialId])`

Nexus registry'de belirtilen tag'in mevcut olup olmadığını kontrol eder.

**Parametreler:**
- `serviceName` (String): Servis/imaj adı
- `version` (String): Tag versiyonu
- `nexusUrl` (String): Nexus REST API URL
- `registryPath` (String): Registry path
- `credentialId` (String, opsiyonel): Jenkins credential ID (varsayılan: `'Nexus_Credentials'`)

**Dönen Değer:** `true` veya `false`

```groovy
def exists = checkTagOnNexus(imageName, env.VERSION, env.NEXUS_URL, env.REGISTRY_PATH, env.NEXUS_CREDENTIAL_ID)
```

---

### `checkDiff(servicePath, prevCommit, currCommit)`

Belirtilen path(ler)de git diff kontrolü yapar.

**Parametreler:**
- `servicePath` (String veya List): Kontrol edilecek path(ler) (örn: `backend/` ya da `['backend/', 'shared/']`). Herhangi biri değişmişse `1` döner. Boş bırakılırsa tüm repo kontrol edilir.
- `prevCommit` (String): Önceki commit hash
- `currCommit` (String): Mevcut commit hash

**Dönen Değer:** `1` (değişiklik var) veya `0` (değişiklik yok)

**`options.failOpen`** — baseline (önceki başarılı commit) yokken ne döneceği:
`false` (varsayılan) → `0`, yani "değişiklik yok"; `true` → `1`, yani "bilinmiyor, build et".
Varsayılan geriye dönük uyumluluk için korundu ama ters yönde güvenlidir: ilk build'de
hiçbir servis deploy edilmez. `Example/Jenkins_example.groovy` bunu çağıran tarafta
`hasBaseline` kontrolüyle telafi ediyor; fonksiyonu doğrudan kullanıyorsanız
`[failOpen: true]` verin.

**Submodule desteği:** Bir path git submodule ise (örn. `--remote` ile checkout edilmişse), submodule kendi reposunda yeni commit almış olsa bile superproject'teki gitlink pointer'ı hiç bump'lanmamış olabilir. Bu tür path'lerde superproject diff'i yerine, submodule'ün şu anki `HEAD`'i superproject'te en son pinlenmiş SHA ile karşılaştırılır — pointer bump şart değildir.

```groovy
def changed = checkDiff(svc.path, env.GIT_PREVIOUS_SUCCESSFUL_COMMIT, env.GIT_COMMIT)
def coklu   = checkDiff(['backend/', 'shared/'], prev, curr)   // birden fazla path
```

---

### `buildAndPushService(config)`

Docker imajını build edip Nexus ve opsiyonel olarak Harbor'a push eder.

**Parametreler (Map):**

| Parametre | Zorunlu | Açıklama |
|-----------|---------|----------|
| `serviceName` | ✅ | Servis/imaj adı |
| `version` | ✅ | Tag versiyonu |
| `servicePath` | ✅ | Servis dizini (Docker context) |
| `dockerfile` | ❌ | Dockerfile yolu (varsayılan: `Dockerfile`) |
| `target` | ❌ | Multi-stage Dockerfile'da build edilecek stage (`--target`) |
| `nexusUrl` | ✅ | Nexus REST API URL |
| `nexusRegistryUrl` | ❌ | Nexus Docker registry URL (varsayılan: `nexusUrl`) |
| `registryPath` | ✅ | Registry path |
| `jenkinsRegistry` | ❌ | Harbor registry adresi (`HOST/PROJECT`) |
| `envFile` | ❌ | .env dosyası yolu |
| `nexusRepoCredentialId` | ❌ | Nexus credential ID (varsayılan: `NEXUS_CREDENTIAL_ID`) |
| `harborCredentialId` | ❌ | Harbor credential ID |
| `pushLatest` | ❌ | `:latest` tag'i de push edilsin mi (varsayılan: `true`). `:latest`, `--cache-from` için kullanıldığından kapatmak build'leri soğutur. |

```groovy
buildAndPushService([
    serviceName      : imageName,
    version          : env.VERSION,
    servicePath      : svc.path,
    dockerfile       : svc.dockerfile ?: 'Dockerfile',
    nexusUrl         : env.NEXUS_URL,
    nexusRegistryUrl : env.NEXUS_REGISTRY_URL,
    registryPath     : env.REGISTRY_PATH,
    jenkinsRegistry  : env.HARBOR_REGISTRY,
    envFile          : svc.env_file,
    target           : svc.target,       // opsiyonel: multi-stage build stage'i
    pushLatest       : svc.push_latest   // opsiyonel: false → :latest atlanır
])
```

> `:latest` tag'i ve Harbor push'u, yerel kısa isimden değil az önce Nexus'a push edilen imaj referansından üretilir — paralel bir job veya cleanup, adımlar arasında yerel tag'i düşürebiliyor.

---

### `pullService(config)`

Sunucuya Docker imajını Nexus'tan pull eder ve local olarak tag'ler.

**Parametreler (Map):**

| Parametre | Zorunlu | Açıklama |
|-----------|---------|----------|
| `serviceName` | ✅ | Servis adı |
| `imageName` | ❌ | İmaj adı (varsayılan: `serviceName`) |
| `version` | ✅ | Tag versiyonu |
| `deployIp` | ✅ | Hedef sunucu IP |
| `nexusUrl` | ✅ | Nexus URL |
| `nexusRegistryUrl` | ❌ | Nexus Docker registry URL |
| `registryPath` | ✅ | Registry path |
| `sshUser` | ❌ | SSH kullanıcı adı (varsayılan: `your-user`) |
| `nexusCredentialId` | ❌ | Nexus credential ID |

```groovy
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
```

---

### `runTestsInContainer(config)`

Container içinde testleri çalıştırır.

**Parametreler (Map):**
- `serviceName` (String): Servis/imaj adı
- `version` (String): Tag versiyonu
- `nexusUrl` (String): Nexus URL
- `nexusRegistryUrl` (String, opsiyonel): Nexus Docker registry URL
- `registryPath` (String): Registry path
- `deployIp` (String): Test sunucusu IP
- `testCommand` (String): Test komutu

```groovy
runTestsInContainer([
    serviceName      : imageName,
    version          : env.VERSION,
    nexusUrl         : env.NEXUS_URL,
    nexusRegistryUrl : env.NEXUS_REGISTRY_URL,
    registryPath     : env.REGISTRY_PATH,
    deployIp         : testTarget,
    testCommand      : svc.test_command
])
```

---

### `deployService(config)`

Servisi sunucuya deploy eder.

**Parametreler (Map):**

| Parametre | Zorunlu | Açıklama |
|-----------|---------|----------|
| `serviceName` | ✅ | Servis adı |
| `version` | ✅ | Tag versiyonu |
| `appName` | ✅ | Uygulama adı (deploy path: `/app/<appName>/`) |
| `deployIp` | ✅ | Deploy sunucusu IP |
| `nexusUrl` | ✅ | Nexus URL |
| `registryPath` | ✅ | Registry path |
| `dockerComposeFile` | ❌ | Docker Compose dosyası (varsayılan: `docker-compose.yml`) |
| `dockerComposeRemoteFile` | ❌ | Uzakta hangi adla saklanacağı |
| `envFile` | ❌ | .env dosyası yolu |
| `extraFiles` | ❌ | Ek dosya/dizin listesi |
| `extraEnvVars` | ❌ | Ek environment değişkenleri (credentials) |
| `shouldPull` | ❌ | Docker imajını pull et (varsayılan: `false`) |
| `imageName` | ❌ | Docker imaj adı (varsayılan: `serviceName`) |
| `sshUser` | ❌ | SSH kullanıcı adı (varsayılan: `your-user`) |
| `sshPort` | ❌ | SSH portu (varsayılan: `22`) |
| `runCompose` | ❌ | `docker compose up -d` çalıştır (varsayılan: `true`) |
| `composeWaitTimeout` | ❌ | `docker compose up --wait` için `--wait-timeout` (saniye, varsayılan: `180`) |
| `pruneImages` | ❌ | Compose sonrası deploy host'unda `docker image prune -a -f` çalıştır (varsayılan: `false`) |
| `composeWait` | ❌ | `up -d --wait` kullan (varsayılan: `true`). İşini yapıp çıkan migration/init container'ı olan projelerde `false` verin — `--wait` böyle bir container'da hata döndürür. |
| `pruneDangling` | ❌ | Compose sonrası dangling imajları temizle (varsayılan: `true`) |

**Compose davranışı:** `docker compose down` artık çalıştırılmıyor — gereksiz kesinti yaratıyordu ve `up -d` zaten yalnızca config/imajı değişen container'ı yeniden oluşturuyor. Sabit `sleep` yerine `up -d --wait` kullanılıyor: container'lar healthy (healthcheck yoksa running) olana kadar beklenir, olmazsa adım hata verir. Compose'dan sonra `docker image prune -f` yalnızca yeni deploy'un dangling bıraktığı imajları siler; başka bir tag'e bağlı imajlara dokunulmaz. Compose dosyasının kopyalanması ile çalıştırılması ayrı takip edilir; aynı host'u paylaşan servislerde dosyayı ilk servis kopyalar, `up`ı yalnızca sonuncusu çalıştırır.

```groovy
deployService([
    serviceName             : imageName,
    version                 : env.VERSION,
    appName                 : env.APP,
    deployIp                : ip,
    nexusUrl                : env.NEXUS_URL,
    registryPath            : env.REGISTRY_PATH,
    dockerComposeFile       : svc.docker_compose_file ?: 'docker-compose.yml',
    dockerComposeRemoteFile : svc.docker_compose_remote_file ?: 'docker-compose.yml',
    envFile                 : svc.env_file,
    extraEnvVars            : credentialsMap,
    extraFiles              : svc.extra_files,
    shouldPull              : false,
    sshUser                 : svc.ssh_user ?: 'your-user',
    sshPort                 : svc.ssh_port ?: 22,
    runCompose              : true,
    pruneImages             : false
])
```

---

### `deployAllServices(config)`

`services.yml`'deki tüm servisleri hedef sunuculara deploy eder. Elle yazılan pull + deploy döngüsünün yerini alır: önce tüm imajlar önden çekilir, sonra yalnızca değişen servisler deploy edilir ve `docker compose up`ın **her host'ta tam olarak bir kez** çalışması garanti edilir (o host'a deploy eden son servis üzerinden çözülür; böylece farklı `deploy_targets` kullanan servisler de doğru ele alınır).

**Parametreler (Map):**

| Parametre | Zorunlu | Açıklama |
|-----------|---------|----------|
| `appName` | ✅ | Uygulama adı (deploy path: `/app/<appName>/`) |
| `version` | ✅ | Tag versiyonu |
| `nexusUrl` | ✅ | Nexus URL |
| `registryPath` | ✅ | Registry path |
| `nexusRegistryUrl` | ❌ | Nexus Docker registry URL (varsayılan: `nexusUrl`) |
| `nexusCredentialId` | ❌ | Nexus credential ID (varsayılan: `NEXUS_CREDENTIAL_ID`) |
| `credentialsMap` | ❌ | `.env`'e yazılacak credentials (`collectCredentials()` çıktısı) |
| `servicesFile` | ❌ | services.yml yolu (varsayılan: `SERVICES_FILE` env ya da `services.yml`) |
| `pruneImages` | ❌ | `deployService`'e aktarılır (varsayılan: `false`) |
| `sshUser` / `sshPort` | ❌ | Servis kendi değerini tanımlamadığında kullanılacak varsayılanlar |

Hedef sunucular şu sırayla çözülür: `DEPLOY_TARGETS` env → servisin kendi `deploy_targets` alanı → `GLOBAL_TARGETS` (Initialize stage'inde `environments.<ortam>`'dan set edilir) → `IP1`..`IP4`.

> `credentialsMap` yalnızca ilk servise değil, deploy edilen **her** servise geçilir. Tüm servisler aynı `/app/<appName>/.env` dosyasını paylaştığı için, bunu ilk servisle sınırlamak, sonraki bir servis değiştiğinde credential güncellemesinin sessizce atlanmasına yol açıyordu.

```groovy
deployAllServices([
    appName         : env.APP,
    version         : env.VERSION,
    nexusUrl        : env.NEXUS_URL,
    nexusRegistryUrl: env.NEXUS_REGISTRY_URL,
    registryPath    : env.REGISTRY_PATH,
    credentialsMap  : collectCredentials(),
    pruneImages     : false
])
```

---

### `dockerCleanup(config)`

Build sonrası Jenkins agent'ında disk alanı boşaltır.

Yalnızca dangling imajlar ve build cache temizlenir, tag'li imajlara dokunulmaz. (`docker system prune -af`, Deploy aşamasının henüz çekmediği imajları da siliyor ve deploy'u bozuyordu.) `keepVersion` (ya da `env.VERSION`) tanımlıysa ve `services.yml` varsa, orada listelenen her imajın **eski tag'leri** de silinir; böylece proje adını Jenkinsfile'a elle yazmak gerekmez ve paylaşımlı agent'ta başka projelerin ya da base imajların (node, dotnet, …) imajlarına hiç dokunulmaz.

**Parametreler (Map, tümü opsiyonel):**

| Parametre | Açıklama |
|-----------|----------|
| `keepVersion` | Korunacak tag (varsayılan: `env.VERSION`) |
| `pruneBuilder` | BuildKit cache'ini de sil (varsayılan: `false`). Açmak build cache'ini yok eder — `buildAndPushService`'in `--cache-from` kazancını iptal eder. |
| `servicesFile` | services.yml yolu (varsayılan: `SERVICES_FILE` env ya da `services.yml`) |

```groovy
dockerCleanup()
```

> Tam temizlik için Deploy stage'inden **sonra** çağırın; aksi halde henüz çekilmemiş imajlar silinebilir.

**extraFiles Formatları:**
```yaml
extra_files:
  - default.conf                    # → /app/myapp/default.conf
  - nginx/nginx.conf                # → /app/myapp/nginx.conf (basename)
  - backend                         # → /app/myapp/backend/ (dizin)
  - src: webrtc/config.env          # Map formatı
    dest: /app/myapp/config.env     # Özel hedef path
```

---

### `trivyScan(tag)`

Build edilen imajlarda DockerScan güvenlik taraması başlatır. Yalnızca `DO_` flag'i `'1'` olan (build edilmiş) servisler taranır.

**Parametreler:**
- `tag` (String): Taranacak imaj tag'i

> `services.yml` dosyası proje root'unda olmalı.

> Bu fonksiyon, Trivy host'una SSH bağlantısı kurarak taramayı **[DockerScan](https://github.com/murat-akpinar/DockerScan)** trigger scripti aracılığıyla çalıştırır. `DOCKERSCAN_HOST` ile tanımlanan sunucuda DockerScan kurulu ve çalışıyor olmalıdır.

```groovy
trivyScan(env.VERSION)
```

---

### `trivyQualityGate(projectName, grade)`

DockerScan Dashboard API'yi sorgulayarak projenin güvenlik notunu kontrol eder.

**Parametreler:**
- `projectName` (String): DockerScan Dashboard'daki proje adı (genellikle `env.APP`)
- `grade` (String, opsiyonel): Minimum geçer not (varsayılan: `'C'`)

**Not Skalası:** `A` (en iyi) → `B` → `C` → `D` → `F` (en kötü)

```groovy
trivyQualityGate(env.APP, 'C')  // A, B, C geçer; D, F pipeline'ı durdurur
trivyQualityGate(env.APP, 'D')  // A, B, C, D geçer; sadece F durdurur
```

---

### `checkovScan(tag)`

Build edilen tüm servisler üzerinde Checkov IaC (Infrastructure as Code) güvenlik taraması çalıştırır. `services.yml`'i dahili olarak okur, yalnızca `DO_` flag'i `'1'` olan servisleri tarar.

**Parametreler:**
- `tag` (String): Taranacak imaj tag'i

**`globalConfig()` üzerinden kontrol:**

| Key | Varsayılan | Açıklama |
|-----|------------|----------|
| `CHECKOV_ENABLED` | `true` | `false` → adımı atla |
| `CHECKOV_SOFT_FAIL` | `true` | `true` → hata olsa pipeline devam eder |
| `CHECKOV_SCRIPT_PATH` | `/app/DockerScan/trigger-checkov.sh` | Uzak sunucudaki tetikleyici script |
| `DOCKERSCAN_HOST` | `YOUR_DOCKERSCAN_HOST_IP` | Uzak tarama sunucusu |
| `DOCKERSCAN_SSH_USER` | `your-user` | SSH kullanıcı adı |

```groovy
checkovScan(env.VERSION)
```

---

### `checkovQualityGate(projectName, grade)`

DockerScan Dashboard API'yi sorgulayarak projenin Checkov güvenlik notunu kontrol eder. Not, IaC kontrollerinin geçme oranından hesaplanır.

**Parametreler:**
- `projectName` (String): DockerScan Dashboard'daki proje adı (genellikle `env.APP`)
- `grade` (String, opsiyonel): Minimum geçer not (varsayılan: `'C'`)

**Not Skalası:** `A` (en iyi) → `B` → `C` → `D` → `F` (en kötü)

**Geçme oranından not hesabı:**

| Not | Geçme Oranı |
|-----|-------------|
| A | %100 |
| B | ≥ %90 |
| C | ≥ %75 |
| D | ≥ %50 |
| F | < %50 |

```groovy
checkovQualityGate(env.APP, 'C')  // A, B, C geçer; D, F pipeline'ı durdurur
checkovQualityGate(env.APP, 'D')  // A, B, C, D geçer; sadece F durdurur
```

---

### `osvScan(tag)`

Build edilen tüm servisler üzerinde OSV-Scanner bağımlılık güvenlik açığı taraması çalıştırır. `services.yml`'i dahili olarak okur, yalnızca `DO_` flag'i `'1'` olan servisleri tarar.

**Parametreler:**
- `tag` (String): Taranacak imaj tag'i

**`globalConfig()` üzerinden kontrol:**

| Key | Varsayılan | Açıklama |
|-----|------------|----------|
| `OSV_ENABLED` | `true` | `false` → adımı atla |
| `OSV_SOFT_FAIL` | `true` | `true` → hata olsa pipeline devam eder |
| `OSV_SCRIPT_PATH` | `/app/DockerScan/trigger-osv.sh` | Uzak sunucudaki tetikleyici script |
| `DOCKERSCAN_HOST` | `YOUR_DOCKERSCAN_HOST_IP` | Uzak tarama sunucusu |
| `DOCKERSCAN_SSH_USER` | `your-user` | SSH kullanıcı adı |

```groovy
osvScan(env.VERSION)
```

---

### `osvQualityGate(projectName, grade)`

DockerScan Dashboard API'yi sorgulayarak projenin OSV-Scanner güvenlik notunu kontrol eder.

**Parametreler:**
- `projectName` (String): DockerScan Dashboard'daki proje adı (genellikle `env.APP`)
- `grade` (String, opsiyonel): Minimum geçer not (varsayılan: `'C'`)

**Not Skalası:** `A` (en iyi) → `B` → `C` → `D` → `F` (en kötü)

```groovy
osvQualityGate(env.APP, 'C')  // A, B, C geçer; D, F pipeline'ı durdurur
osvQualityGate(env.APP, 'D')  // A, B, C, D geçer; sadece F durdurur
```

---

### `sendEmailNotification(config)`

Pipeline sonuçlarını HTML e-posta ile bildirir. `emailTeams()` ile tanımlı takım anahtarlarını otomatik olarak e-posta adreslerine çevirir.

**Parametreler (Map):**
- `status` (String): `'success'`, `'failure'` veya `'aborted'`
- `appName` (String): Uygulama adı
- `recipientEmail` (String): Alıcı adresi veya `emailTeams()` anahtarı (virgülle birden fazla)
- `nexusUrl` (String, opsiyonel): Nexus URL

```groovy
sendEmailNotification([
    status        : 'success',
    appName       : env.APP,
    recipientEmail: env.RECIPIENT_EMAIL,  // örn: 'Takim_1, Takim_2'
    nexusUrl      : env.NEXUS_URL
])
```

---

### `emailTeams()`

Takım adı → e-posta adresi eşlemesini döndürür. `sendEmailNotification()` tarafından otomatik kullanılır.

**Dönen Değer:** `Map<String, String>`

`emailTeams.groovy` dosyasını takım listesi ve e-posta adresleriyle doldurun:

```groovy
def call() {
    return [
        'Takim_1': 'dev1@company.com, dev2@company.com',
        'Takim_2': 'ops@company.com',
    ]
}
```

`services.yml`'de `recipients: 'Takim_1, Takim_2'` ile kullanılır.

---

## services.yml Yapılandırması

`services.yml` projenin yapılandırmasını, ortamlarını ve servislerini tanımlar.

### Tam Yapı

```yaml
app_name: my-project                   # Uygulama adı (deploy path: /app/my-project/)
recipients: 'Takim_1, Takim_2'         # E-posta alıcıları (emailTeams anahtarı veya direkt adres)

environments:                          # Ortam başına deploy hedefleri
  test:
    deploy_targets:
      - 192.168.1.10
      - 192.168.1.11
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
    docker_compose_file: docker-compose.yml       # Compose dosyası (opsiyonel)
    docker_compose_remote_file: docker-compose.yml # Uzakta hangi adla (opsiyonel)
    extra_files:                       # Ek dosya/dizin listesi (opsiyonel)
      - { src: "default.conf", dest: "/app/my-project/default.conf" }
    ssh_user: your-user                # SSH kullanıcı adı (opsiyonel)
    test_command: "npm test"           # Test komutu (opsiyonel)

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend
    env_file: frontend/.env
```

### Alan Açıklamaları

**Üst Seviye Alanlar:**

| Alan | Zorunlu | Açıklama |
|------|---------|----------|
| `app_name` | ✅ | Uygulama adı (pipeline `env.APP` olarak okunur) |
| `recipients` | ❌ | E-posta alıcıları (virgülle ayrılmış takım anahtarı veya adres) |
| `environments` | ✅ | Ortam başına deploy hedefleri |

**Servis Alanları:**

| Alan | Zorunlu | Açıklama |
|------|---------|----------|
| `name` | ✅ | Servis adı |
| `path` | ✅ | Servis dizini (git diff için) |
| `dockerfile` | ✅ | Dockerfile yolu |
| `image_name` | ✅ | Docker imaj adı |
| `env_file` | ❌ | .env dosyası yolu (ilk servis için Git'ten kopyalanır) |
| `docker_compose_file` | ❌ | Compose dosyası yolu |
| `docker_compose_remote_file` | ❌ | Sunucuda hangi adla saklanacağı |
| `extra_files` | ❌ | Ek dosya/dizin listesi |
| `ssh_user` | ❌ | SSH kullanıcı adı |
| `test_command` | ❌ | Test komutu (Run Tests stage'inde çalıştırılır) |

### .env Dosyası Yönetimi

- **İlk servis:** `env_file` belirtilmişse Git'teki dosya kullanılır; `VERSION` ve credentials eklenir/güncellenir
- **Diğer servisler:** Sunucudaki mevcut `.env` okunur, sadece `VERSION` güncellenir
- Tüm servisler `/app/<app_name>/.env` ortak dosyasını kullanır

---

## Multi-Server Deployment

Deploy hedefleri `services.yml`'deki `environments` bölümünden okunur:

```yaml
environments:
  test:
    deploy_targets:
      - 192.168.1.10
      - 192.168.1.11
  prod:
    deploy_targets:
      - 192.168.3.10
```

Pipeline, seçilen `ENVIRONMENT` parametresine göre (`test`, `preprod`, `prod`) ilgili `deploy_targets` listesini kullanır. Tüm hedeflere sırayla deploy yapılır.

---

## Sorun Giderme

**services.yml bulunamadı:**
- Dosyanın proje root dizininde olduğundan emin olun
- Git'e commit edildiğinden emin olun

**Shared library bulunamadı:**
- Jenkins → Manage Jenkins → Configure System → Global Pipeline Libraries
- `devops-jenkins-library` tanımlı mı kontrol edin

**readYaml hatası:**
- Pipeline Utility Steps plugin yüklü mü kontrol edin

**extra_files kopyalanmıyor:**
- `services.yml`'de `extra_files` tanımlı mı kontrol edin
- Log'larda `extra file/dizin kopyalanacak...` mesajını kontrol edin

Detaylı sorun giderme için: **[docs/SSS.md](docs/SSS.md)**

---

## Belgeler

- **[docs/01-cicd-hazirliklari.md](docs/01-cicd-hazirliklari.md)** — CI/CD pipeline öncesi hazırlık rehberi
- **[docs/02-pipeline-kurulumu.md](docs/02-pipeline-kurulumu.md)** — Pipeline kurulum adımları
- **[docs/03-jenkins-plugin-kurulumu.md](docs/03-jenkins-plugin-kurulumu.md)** — Gerekli Jenkins eklentileri ve yapılandırma
- **[docs/KULLANIM_KILAVUZU.md](docs/KULLANIM_KILAVUZU.md)** — Detaylı kullanım kılavuzu
- **[docs/SSS.md](docs/SSS.md)** — Sık sorulan sorular
- **[Example/Jenkins_example.groovy](Example/Jenkins_example.groovy)** — Tam Jenkinsfile örneği
- **[Example/services_example.yml](Example/services_example.yml)** — services.yml örneği

---

## Katkıda Bulunma

1. Değişikliklerinizi yapın
2. Test edin
3. Pull Request oluşturun

---

## Lisans

Bu proje kurumsal kullanım içindir.

---

## Davranış Değişiklikleri

Bu sürümde varsayılan davranışı etkileyen değişiklikler. Kütüphaneyi sürüm etiketiyle
sabitlemek için: `@Library('devops-jenkins-library@v2.0') _`

| Değişiklik | Etki | Geri alma |
|-----------|------|-----------|
| `docker compose down` artık çalıştırılmıyor; yerine `up -d --wait` | Deploy kesintisi azalır; container'lar sağlıklı olana kadar beklenir | `composeWait: false` |
| `--wait` / `--wait-timeout` yalnızca host'un Compose sürümü destekliyorsa eklenir (≥ 2.1.1 / ≥ 2.17) | Eski host'larda sessizce düz `up -d`'ye dönülür | — (otomatik) |
| Compose sonrası deploy host'unda `docker image prune -f` | Yeni deploy'un dangling bıraktığı imajlar silinir; host geneli çalışır | `pruneDangling: false` |
| `buildAndPushService` artık Nexus'a `docker login` yapıyor | Agent'ın pipeline dışında login edilmiş olması gerekmiyor | — |
| Build'e `BUILDKIT_INLINE_CACHE=1` eklendi | `--cache-from` gerçekten çalışıyor (önceden sessizce boşa gidiyordu) | — |
| `dockerCleanup` artık BuildKit cache'ini **silmiyor** | Sonraki build'ler daha hızlı | `pruneBuilder: true` |
| `collectCredentials` adında rakam geçen değişkenleri de topluyor | `AWS_S3_SECRET`, `DB2_PASSWORD` gibi credential'lar önceden **sessizce atlanıyordu**; artık `.env`'e yazılıyorlar | — |
| `sonarQubeQualityGate` 10 dakikalık timeout ile çalışıyor | Webhook gelmezse adım süresiz asılı kalmıyor | `timeoutMinutes: N` |
| checkov/osv quality gate yalnızca bu build'in tag'ini değerlendiriyor | Eski bir tag'in kötü notu sonraki build'leri fail ettirmiyor | — |
| Checkov/OSV taramasına `.env`, `node_modules`, `.git` vb. **gönderilmiyor** | Sırlar tarama sunucusunda kalmıyor, yükleme hızlanıyor | — |
| Tüm `ssh`/`scp` çağrıları bağlantı çoğullama kullanıyor | Deploy başına ~100 SSH handshake yerine host başına tek oturum | `sshOptions([multiplex: false])` |

**Güvenlik düzeltmesi:** `pullService`, `runTestsInContainer` ve `trivyScan` Nexus şifresini
shell komut satırına gömüyordu; değer agent'ta (ve DockerScan sunucusunda) `ps aux` ile
okunabiliyordu. Artık stdin üzerinden aktarılıyor. Şifresinde tek tırnak bulunan kurulumlar
önceden çalışmıyordu, artık çalışıyor.

**Doğrulama:** `groovy tools/syntax-check.groovy` ile `vars/` sözdizimi denetlenir; her
push/PR'da GitHub Actions üzerinden otomatik çalışır.
