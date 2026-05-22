# CI/CD Pipeline Kurulumu (Adım 2)

> For English documentation: **[02-pipeline-setup.md](./02-pipeline-setup.md)**

Bu döküman, hazırlıklar tamamlandıktan sonra Jenkins pipeline'ının oluşturulması için gerekli dosyaların (Jenkinsfile ve services.yml) hazırlanmasını anlatır.

---

## 1. Jenkinsfile Oluşturma

`Jenkinsfile`, pipeline adımlarınızı tanımlar. Tam örnek bu repo'da bulunur:

- `Example/Jenkins_example.groovy`

### Önerilen yol
1. Proje köküne `Jenkinsfile` kopyalayın veya oluşturun.
2. `vars/globalConfig.groovy` dosyasını kendi ortamınıza göre doldurun (Nexus, Harbor, Trivy, SonarQube adresleri).
3. `environment` bloğuna proje credential'larını ekleyin (otomatik `.env` dosyasına yazılır).

### Pipeline Parametreleri

```groovy
parameters {
    choice(name: 'ENVIRONMENT', choices: ['test', 'preprod', 'prod'], description: 'Deploy ortamını seçin')
    booleanParam(name: 'FORCE_REBUILD', defaultValue: false, description: 'Tüm imajları zorla yeniden build et')
    booleanParam(name: 'USE_EXISTING_IMAGE', defaultValue: false, description: 'Tag mevcutsa build/push atla')
    string(name: 'VERSION_TAG', defaultValue: 'test-v1.0', description: 'Deploy edilecek imaj tag')
}
```

### Credentials Tanımlama

```groovy
environment {
    VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"

    // Credentials — otomatik olarak .env dosyasına yazılır
    PSQL_HOST = credentials('psql_myproject_host')
    PSQL_PASS = credentials('psql_myproject_pass')
}
```

> Not: Ayrıntılı kullanım için `KULLANIM_KILAVUZU.md` dosyasına bakın.

---

## 2. services.yml Dosyası Oluşturma

`services.yml`, pipeline'ın hangi servisleri hangi parametrelerle işleyeceğini tanımlar. Örnek:

- `Example/services_example.yml`

### Tam yapı

```yaml
app_name: my-project                   # Uygulama adı — deploy path: /app/my-project/
recipients: 'Takim_1, Takim_2'         # E-posta alıcıları

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
  - name: backend
    path: backend/
    dockerfile: Dockerfile
    image_name: myapp_backend
    env_file: backend/.env
    extra_files:
      - { src: "default.conf", dest: "/app/my-project/default.conf" }
    test_command: "npm test"

  - name: frontend
    path: frontend/
    dockerfile: Dockerfile
    image_name: myapp_frontend
    env_file: frontend/.env
```

### Dikkat edilmesi gerekenler

- `app_name` zorunludur — pipeline `env.APP` değerini buradan okur
- `environments` altındaki `deploy_targets`, pipeline parametresindeki `ENVIRONMENT` seçimiyle eşleşmelidir
- `image_name` Nexus/Harbor registry'deki imaj adıyla uyumlu olmalıdır

---

## 3. Jenkins'te Pipeline Job Oluşturma

1. Jenkins UI → New Item → Pipeline
2. SCM olarak Git repo URL'inizi girin; gerekirse credential ekleyin
3. Script Path: `Jenkinsfile` (repo kökündeyse)
4. Kaydedin ve çalıştırın

İlk koşuda:
- `FORCE_REBUILD: true` seçin (tag yoksa build'i zorlar)
- `ENVIRONMENT` seçin (services.yml'deki ortamla eşleşmeli)
- Credential ve SSH erişimlerinin hazır olduğundan emin olun

---

## Kontrol Listesi

- [ ] `Jenkinsfile` proje kökünde oluşturuldu
- [ ] `vars/globalConfig.groovy` kendi ortamına göre güncellendi
- [ ] `services.yml` oluşturuldu — `app_name`, `environments`, `services` alanları dolu
- [ ] Jenkins job oluşturuldu ve SCM bağlantısı doğrulandı
- [ ] Jenkins credentials tanımlı (`Nexus_Credentials` vb.)
- [ ] İlk build `FORCE_REBUILD: true` ile başarıyla tamamlandı
