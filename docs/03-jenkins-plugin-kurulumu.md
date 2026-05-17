# Jenkins Plugin Kurulum Rehberi

Bu rehber, shared library'nin çalışması için Jenkins'e yüklenmesi gereken eklentileri listeler.

---

## Zorunlu Eklentiler

| Eklenti Adı | Plugin ID | Kullanıldığı Yer |
|-------------|-----------|-----------------|
| **Pipeline (Workflow Aggregator)** | `workflow-aggregator` | Declarative Pipeline, `@Library`, shared library desteği |
| **Git** | `git` | Shared library'yi Git SCM'den çekmek için |
| **Pipeline Utility Steps** | `pipeline-utility-steps` | `readYaml`, `readFile`, `writeFile`, `fileExists` adımları |
| **SonarQube Scanner** | `sonar` | `withSonarQubeEnv()`, `waitForQualityGate()` |
| **Email Extension** | `email-ext` | `emailext()` — HTML e-posta bildirimleri |
| **Credentials Binding** | `credentials-binding` | `withCredentials()`, `usernamePassword()` |
| **Credentials** | `credentials` | Jenkins credential yönetimi (Nexus, Harbor) |

---

## Kurulum Adımları

### Yöntem 1: Jenkins UI (Önerilen)

1. **Manage Jenkins** → **Plugins** → **Available plugins** sekmesine gidin
2. Arama kutusuna her eklenti adını yazın ve yanındaki kutuyu işaretleyin
3. Tüm eklentileri seçtikten sonra **Install** butonuna tıklayın
4. Kurulum tamamlandığında **Restart Jenkins when installation is complete** seçeneğini işaretleyin

### Yöntem 2: Jenkins CLI

```bash
# Jenkins CLI ile toplu kurulum
java -jar jenkins-cli.jar -s http://JENKINS_URL/ install-plugin \
  workflow-aggregator \
  git \
  pipeline-utility-steps \
  sonar \
  email-ext \
  credentials-binding \
  credentials
```

### Yöntem 3: plugins.txt (Docker/IaC kurulumları için)

Jenkins Docker imajı kullanıyorsanız `plugins.txt` dosyasına ekleyin:

```
workflow-aggregator:latest
git:latest
pipeline-utility-steps:latest
sonar:latest
email-ext:latest
credentials-binding:latest
credentials:latest
```

---

## Eklenti Sonrası Yapılandırma

### SonarQube Sunucusu Tanımlama

`sonarQubeAnalysis()` fonksiyonunun çalışabilmesi için SonarQube sunucusunun Jenkins'te tanımlanması gerekir:

1. **Manage Jenkins** → **Configure System** → **SonarQube servers**
2. **Add SonarQube** butonuna tıklayın
3. Aşağıdaki bilgileri girin:

   | Alan | Değer |
   |------|-------|
   | **Name** | `globalConfig.groovy`'deki `SONAR_SERVER` değeriyle aynı olmalı |
   | **Server URL** | SonarQube sunucusu URL'i |
   | **Server authentication token** | SonarQube'den oluşturulan token (Jenkins credential olarak ekleyin) |

4. **Save** tıklayın

### E-posta Sunucusu Tanımlama

`sendEmailNotification()` fonksiyonunun çalışabilmesi için SMTP sunucusu yapılandırması gerekir:

1. **Manage Jenkins** → **Configure System** → **Extended E-mail Notification**
2. SMTP sunucu bilgilerini girin:

   | Alan | Açıklama |
   |------|----------|
   | **SMTP server** | SMTP sunucu adresi |
   | **SMTP Port** | Genellikle `587` (TLS) veya `465` (SSL) |
   | **Credentials** | SMTP kullanıcı adı ve şifresi |
   | **Use SSL/TLS** | SMTP sunucunuza göre işaretleyin |

3. **Default Recipients** alanına varsayılan alıcı ekleyebilirsiniz
4. **Save** tıklayın

---

## Kurulum Doğrulama

Tüm eklentiler kurulduktan sonra aşağıdaki testleri yapın:

```groovy
// Test pipeline — Jenkins'te yeni bir Pipeline job oluşturup çalıştırın
pipeline {
    agent any
    stages {
        stage('Plugin Test') {
            steps {
                script {
                    // Pipeline Utility Steps testi
                    writeFile file: 'test.yaml', text: 'key: value'
                    def data = readYaml file: 'test.yaml'
                    echo "readYaml çalışıyor: ${data.key}"
                    sh 'rm test.yaml'
                }
            }
        }
    }
}
```

---

## Sık Karşılaşılan Sorunlar

**`readYaml` adımı bulunamıyor:**
- **Pipeline Utility Steps** eklentisinin kurulu olduğunu kontrol edin
- Jenkins'i restart edin

**`withSonarQubeEnv` adımı bulunamıyor:**
- **SonarQube Scanner** eklentisinin kurulu olduğunu kontrol edin
- **Manage Jenkins** → **Configure System** → **SonarQube servers** bölümünde sunucu tanımlı olduğunu kontrol edin

**`emailext` adımı bulunamıyor:**
- **Email Extension** eklentisinin kurulu olduğunu kontrol edin
- **Manage Jenkins** → **Configure System** → **Extended E-mail Notification** bölümünde SMTP ayarlarının yapıldığını kontrol edin

**`withCredentials` / `usernamePassword` çalışmıyor:**
- **Credentials Binding** eklentisinin kurulu olduğunu kontrol edin
- İlgili credential'ların Jenkins'te tanımlı olduğunu kontrol edin (`Nexus_Credentials`, `Harbor_Credentials`)

---

## Sonraki Adım

Plugin kurulumu tamamlandıktan sonra:

1. `globalConfig.groovy` dosyasını ortamınıza göre düzenleyin
2. Jenkins credentials tanımlayın (`Nexus_Credentials`, `Harbor_Credentials`)
3. Shared library'yi Jenkins'e tanıtın

Detaylı bilgi için [`01-cicd-hazirliklari.md`](./01-cicd-hazirliklari.md) ve [`02-pipeline-kurulumu.md`](./02-pipeline-kurulumu.md) dosyalarına bakın.
