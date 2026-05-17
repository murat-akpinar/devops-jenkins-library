# ❓ Sık Sorulan Sorular (SSS)

Jenkins Shared Library kullanımında sık karşılaşılan sorular ve cevapları.

## 📋 İçindekiler

- [Credentials ve Environment Variables](#-credentials-ve-environment-variables)
- [.env Dosyası Kullanımı](#-env-dosyası-kullanımı)
- [services.yml Kullanımı](#-servicesyml-kullanımı)
- [extra_files Kullanımı](#-extra_files-kullanımı)
- [Docker Compose ve Version Tag](#-docker-compose-ve-version-tag)
- [Deploy İşlemi](#-deploy-işlemi)
- [Sorun Giderme](#-sorun-giderme)

---

## 🔐 Credentials ve Environment Variables

### Q1: Environment bloğunda credentials'ları nasıl tanımlarım?

**Cevap:** `environment` bloğunda `credentials()` fonksiyonu ile tanımlayabilirsiniz. Yazdırma işlemi otomatik olarak yapılır.

```groovy
environment {
    VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"
    APP = 'my-app'
    
    // Secret text credentials (masked in logs)
    PSQL_HOST = credentials('psql_host')
    PSQL_PASS = credentials('psql_pass')
    JWT_SECRET_KEY = credentials('jwt_secret_key')
}
```

**Önemli:** 
- Sadece `environment` bloğunda tanımlamanız yeterli
- `deployService()` fonksiyonu otomatik olarak bunları `.env` dosyasına yazar
- Jenkins loglarında maskelenir

---

### Q2: Credentials'ları manuel olarak .env dosyasına yazmam gerekiyor mu?

**Cevap:** Hayır! `deployService()` fonksiyonu otomatik olarak `environment` bloğundaki tüm credentials'ları `.env` dosyasına yazar.

**Nasıl çalışıyor:**
1. `environment` bloğunda credentials tanımlarsınız
2. `collectCredentials()` fonksiyonu credentials'ları otomatik toplar
3. `deployService()` çağrıldığında otomatik olarak `.env` dosyasına yazar
4. Sistem değişkenleri (`JENKINS_*`, `BUILD_*`, vb.) otomatik filtreler

Jenkinsfile'da ayrı bir yazdırma işlemi yapmanıza gerek yok!

---

### Q3: Herhangi bir isimle credentials tanımlayabilir miyim?

**Cevap:** Evet, ancak sistem değişkeni prefix'leri ile başlamamalı.

**✅ Çalışır:**
```groovy
environment {
    MY_SECRET = credentials('some-secret')      // → .env'de MY_SECRET=... olur
    CUSTOM_TOKEN = credentials('custom-token')  // → .env'de CUSTOM_TOKEN=... olur
    PSQL_HOST = credentials('psql_host')        // → .env'de PSQL_HOST=... olur
}
```

**❌ Çalışmaz (hariç tutulur):**
```groovy
environment {
    JENKINS_SECRET = credentials('secret')  // → .env'ye YAZILMAZ!
    BUILD_TOKEN = credentials('token')      // → .env'ye YAZILMAZ!
    DO_FLAG = credentials('flag')           // → .env'ye YAZILMAZ!
}
```

**Hariç tutulan prefix'ler:**
- `JENKINS_*`, `BUILD_*`, `DO_*`, `TAG_EXISTS_*`
- `GIT_*`, `JOB_*`, `NODE_*`, `HUDSON_*`

---

## 📄 .env Dosyası Kullanımı

### Q4: services.yml'de env_file belirtmem zorunlu mu?

**Cevap:** Hayır, `env_file` belirtmek **opsiyonel**dir.

**Senaryo 1: env_file belirtilmemiş**
```yaml
services:
  - name: backend
    # env_file belirtilmedi
```
→ Eğer `environment` bloğunda credentials varsa, otomatik olarak `.env` dosyası oluşturulur.

**Senaryo 2: env_file belirtilmiş**
```yaml
services:
  - name: backend
    env_file: .env.prod  # veya .env.dev, backend/.env vs.
```
→ Belirtilen dosya okunur, içeriğine `VERSION` + credentials eklenir, sunucuya `.env` olarak kopyalanır.

---

### Q5: .env.prod, .env.dev gibi farklı environment dosyaları kullanabilir miyim?

**Cevap:** Evet! `services.yml`'de belirttiğiniz `.env.prod`, `.env.dev` gibi dosyaları kullanabilirsiniz.

**Örnek:**
```yaml
# services.yml
services:
  - name: backend
    env_file: .env.prod  # Production için
```

**Nasıl çalışıyor:**
1. Repo'daki `.env.prod` dosyası okunur
2. İçeriğine `VERSION=${version}` eklenir/güncellenir
3. Environment'taki credentials'lar eklenir/güncellenir
4. Sunucuya `/app/${APP}/.env` olarak kopyalanır

**Sonuç:** Repo'da `.env.prod` olarak saklanır, sunucuda `.env` olarak kullanılır (Docker Compose için).

---

### Q6: Birden fazla servis için farklı .env dosyaları kullanabilir miyim?

**Cevap:** Teknik olarak mümkün, ancak **önerilmez**. Sunucuda tüm servisler için tek bir merkezi `.env` dosyası kullanılır.

**Örnek:**
```yaml
services:
  - name: backend
    env_file: backend/.env  # İlk servis için kullanılır
  - name: frontend
    env_file: frontend/.env  # Belirtilse bile kullanılmaz
```

**Nasıl çalışıyor:**
- İlk servis (backend) deploy edilirken `backend/.env` Git'ten kopyalanır, `/app/${APP}/.env` olarak sunucuya yazılır
- Diğer servisler (frontend) deploy edilirken sunucudaki mevcut `.env` okunur, sadece `VERSION` güncellenir
- Frontend'in `env_file` belirtilse bile kullanılmaz (backend'in `.env`'i korunur)

**Öneri:** Tek bir merkezi `.env` dosyası kullanmak daha basit ve yönetilebilirdir.

---

### Q7: Manuel olarak sunucuya girip `docker compose up -d` yaptığımda .env dosyası okunur mu?

**Cevap:** Evet! Docker Compose otomatik olarak `.env` dosyasını okur.

**Sunucuda:**
```bash
cd /app/my-app
docker compose up -d
```
→ Docker Compose otomatik olarak aynı dizindeki `.env` dosyasını okur.

**Not:** `--env-file .env` parametresi gereksizdir (ama zarar vermez). Docker Compose varsayılan olarak `.env` dosyasını okur.

---

### Q8: .env dosyasının içeriği korunur mu? Repo'da varsa ezilir mi?

**Cevap:** İçerik korunur, ezilmez! Mevcut içeriğe `VERSION` ve credentials eklenir/güncellenir.

**Örnek:**

**Repo'daki `.env.prod`:**
```bash
DB_HOST=prod-db.example.com
DB_PORT=5432
API_URL=https://api.prod.com
```

**İlk servis deploy sonrası sunucudaki `.env`:**
```bash
# Repo'daki içerik korunur
DB_HOST=prod-db.example.com
DB_PORT=5432
API_URL=https://api.prod.com

# Otomatik eklenenler
VERSION=test-v1.0
PSQL_HOST=...
PSQL_PASS=...
```

**Diğer servisler deploy edildiğinde:**
- Sunucudaki mevcut `.env` okunur (repo'daki içerik korunur)
- Sadece `VERSION` güncellenir (diğer değişkenler korunur)

---

## 🔧 services.yml Kullanımı

### Q9: Her servis için env_file belirtmem gerekiyor mu?

**Cevap:** Hayır, her servis için ayrı ayrı `env_file` belirtmenize gerek yok. **Sadece ilk servis için** belirtmeniz yeterli.

**Tek bir merkezi .env:**
```yaml
services:
  - name: backend
    env_file: .env  # İlk servis için belirtilir
  - name: frontend
    # env_file belirtilmedi → backend'in oluşturduğu .env kullanılır
```

**Nasıl çalışıyor:**
- Backend deploy: Git'teki `.env` kopyalanır, `/app/${APP}/.env` olarak sunucuya yazılır
- Frontend deploy: Sunucudaki mevcut `.env` okunur, sadece `VERSION` güncellenir

---

### Q10: services.yml'de env_file belirtmesem ne olur?

**Cevap:** 
- **İlk servis için:** Eğer `environment` bloğunda credentials varsa → Otomatik `.env` oluşturulur. Sadece `VERSION` varsa → Sadece `VERSION` ile `.env` oluşturulur
- **Diğer servisler için:** Sunucudaki mevcut `.env` okunur, sadece `VERSION` güncellenir

**Örnek:**
```groovy
environment {
    VERSION = 'test-v1.0'
    APP = 'my-app'
    PSQL_HOST = credentials('psql_host')  // Credential var
}
```

```yaml
services:
  - name: backend
    # env_file belirtilmedi
```

→ İlk servis (backend) deploy edilirken otomatik `.env` oluşturulur, içine `VERSION` + `PSQL_HOST` yazılır. Diğer servisler bu `.env`'i kullanır.

---

## 📁 extra_files Kullanımı

### Q11: extra_files'da sadece dosya adı yazsam yeterli mi?

**Cevap:** Evet! `dest` belirtmeden sadece dosya/dizin yolunu yazmanız yeterli. Otomatik olarak `/app/<APP>/<dosya_adı>` konumuna kopyalanır.

**Örnek:**
```yaml
extra_files:
  - default.conf              # → /app/myapp/default.conf
  - nginx/nginx.conf          # → /app/myapp/nginx.conf (basename ile)
  - backend                   # → /app/myapp/backend/ (dizin)
```

**Map formatı (özel konum için):**
```yaml
extra_files:
  - src: webrtc/config.env
    dest: /app/myapp/config.env  # Özel konum
```

---

### Q12: extra_files'da dosya mı dizin mi olduğunu belirtmem gerekiyor mu?

**Cevap:** Hayır! Sistem otomatik olarak kaynağın dosya mı dizin mi olduğunu tespit eder ve buna göre kopyalar.

**Nasıl çalışıyor:**
- Dosya ise: Normal `scp` ile kopyalanır
- Dizin ise: `scp -r` ile kopyalanır
- Sunucuda hedef path farklı türde (dosya/dizin) varsa otomatik temizlenir

**Örnek:**
```yaml
extra_files:
  - default.conf    # Dosya → /app/myapp/default.conf
  - config/         # Dizin → /app/myapp/config/
  - backend         # Dizin → /app/myapp/backend/
```

**Önemli Not:** Sunucuda `/app/myapp/default.conf` dizin olarak varsa ve siz dosya kopyalıyorsanız, sistem otomatik olarak dizini siler ve dosyayı kopyalar (Docker mount hatalarını önler).

---

### Q13: extra_files kopyalama işlemi ne zaman yapılır?

**Cevap:** `extra_files` kopyalama işlemi `.env` dosyası kopyalamasından **önce** yapılır. Bu sayede Docker Compose dosyası bu dosyalara bağımlıysa, dosyalar hazır olur.

**İşlem Sırası:**
1. Docker imajı pull edilir
2. **extra_files kopyalanır** (`.env`'den önce)
3. `.env` dosyası oluşturulur/kopyalanır
4. Docker Compose dosyası kopyalanır
5. `docker compose up -d` çalıştırılır

---

### Q14: extra_files kopyalanmıyor, ne yapmalıyım?

**Kontrol edin:**
1. `services.yml`'de `extra_files` tanımlı mı?
2. Log'larda `📁 extra file/dizin kopyalanacak...` mesajı görünüyor mu?
3. Kaynak dosya/dizin projede mevcut mu?
4. Log'larda hata mesajı var mı?

**Örnek Log:**
```
📁 3 adet extra file/dizin kopyalanacak...
📦 Kaynak türü: dosya (default.conf)
✅ dosya kopyalandı: default.conf → /app/myapp/default.conf
```

Eğer log'da `ℹ️ extraFiles boş veya tanımlı değil` mesajı görünüyorsa, `services.yml`'de `extra_files` tanımlanmamış demektir.

---

### Q15: extra_files'da birden fazla servis için farklı dosyalar tanımlayabilir miyim?

**Cevap:** Evet! Her servis için kendi `extra_files` listesini tanımlayabilirsiniz.

**Örnek:**
```yaml
services:
  - name: backend
    path: backend
    dockerfile: Dockerfile
    image_name: myapp_backend
    extra_files:
      - backend
      - frontend
      - nginx
    
  - name: frontend
    path: frontend
    dockerfile: Dockerfile
    image_name: myapp_frontend
    # extra_files yok
    
  - name: nginx
    path: nginx
    dockerfile: Dockerfile
    image_name: myapp_nginx
    extra_files:
      - default.conf
      - nginx/nginx.conf
```

Her servis deploy edilirken kendi `extra_files` listesini kopyalar.

---

## 🐳 Docker Compose ve Version Tag

### Q16: Docker Compose dosyasında version tag nasıl kullanılır?

**Cevap:** Docker Compose dosyasında `${VERSION}` değişkeni kullanın. `.env` dosyasından otomatik okunur.

**docker-compose.yml:**
```yaml
services:
  backend:
    image: myapp_backend:${VERSION}  # .env dosyasından otomatik okunur
    environment:
      - VERSION=${VERSION}
```

**Nasıl çalışıyor:**
1. Jenkins → `VERSION_TAG` parametresi alınır
2. `environment` bloğunda `VERSION = "${params.VERSION_TAG}"` tanımlanır
3. `deployService()` → `.env` dosyasına `VERSION=test-v1.0` yazar
4. Docker Compose → `.env` dosyasını otomatik okur
5. `${VERSION}` değişkeni `.env` dosyasından okunur

---

### Q17: Docker Compose dosyasını runtime'da modify etmem gerekiyor mu?

**Cevap:** Hayır! Docker Compose dosyasını modify etmeye gerek yok. `.env` dosyası ile version yönetimi yapılır.

**❌ Yanlış yaklaşım:**
```groovy
sh "sed -i 's/:latest/:${env.VERSION}/g' docker-compose.yml"  // ÖNERİLMİYOR
```

**✅ Doğru yaklaşım (mevcut sistem):**
```yaml
# docker-compose.yml (repo'da)
services:
  backend:
    image: myapp_backend:${VERSION}  # .env'den okunur
```

---

## 🚀 Deploy İşlemi

### Q18: deployService() hangi dosyada credentials'ları .env'ye yazıyor?

**Cevap:** `vars/deployService.groovy` dosyasında.

**İşlem akışı:**
1. `collectCredentials()` fonksiyonu environment bloğundaki credentials'ları toplar
2. `deployService()` içinde:
   - `envFile` varsa Git'teki dosya okunur, yoksa sunucudaki mevcut `.env` okunur
   - `VERSION` eklenir/güncellenir
   - `extraEnvVars` (credentials) eklenir/güncellenir
   - Sunucuya `/app/${appName}/.env` olarak kopyalanır

---

### Q19: Deploy path'i nasıl belirleniyor?

**Cevap:** Deploy path otomatik olarak `/app/${APP}/` olarak ayarlanır.

**Örnek:**
```groovy
environment {
    APP = 'my-project'
}
```
→ Deploy path: `/app/my-project/`

**Not:** `deployService()` fonksiyonu path'i otomatik olarak `/app/${appName}/` olarak ayarlar. Her projede ayrı ayrı belirtmenize gerek yok.

---

### Q20: Deploy sonrası .env dosyası nerede saklanır?

**Cevap:** Sunucuda `/app/${APP}/.env` olarak saklanır.

**Örnek:**
- `APP = 'my-project'` → `/app/my-project/.env`
- `APP = 'my-app'` → `/app/my-app/.env`

Docker Compose bu dosyayı otomatik olarak okur (aynı dizinde olduğu için).

---

## 🐛 Sorun Giderme

### Q21: .env dosyasında VERSION görünmüyor

**Kontrol edin:**
1. `environment` bloğunda `VERSION` tanımlı mı?
2. `deployService()` çağrısında `version` parametresi geçiliyor mu?
3. Log'larda `.env` dosyası oluşturuldu mu?

**Çözüm:**
```groovy
environment {
    VERSION = "${params.VERSION_TAG ?: 'test-v1.0'}"
}
```

---

### Q22: Credentials .env dosyasına yazılmıyor

**Kontrol edin:**
1. `environment` bloğunda credentials tanımlı mı?
2. Credentials ismi sistem değişkeni prefix'i ile başlıyor mu? (`JENKINS_`, `BUILD_`, vs.)
3. `collectCredentials()` çağrılıyor mu?
4. `deployService()`'e `extraEnvVars` geçiliyor mu?

**Çözüm:**
```groovy
// ❌ Yanlış
JENKINS_SECRET = credentials('secret')  // Hariç tutulur

// ✅ Doğru
MY_SECRET = credentials('secret')  // Yazılır

// Deploy stage'inde
def credentialsMap = collectCredentials()
deployService([
    // ...
    extraEnvVars: credentialsMap
])
```

---

### Q23: Docker Compose .env dosyasını okumuyor

**Kontrol edin:**
1. `.env` dosyası `/app/${APP}/` dizininde mi?
2. Docker Compose dosyası aynı dizinde mi?
3. `.env` dosyasında `VERSION=...` satırı var mı?

**Manuel test:**
```bash
cd /app/my-app
cat .env  # İçeriği kontrol edin
docker compose config  # Docker Compose değişkenlerini gösterir
```

---

### Q24: extra_files kopyalanmıyor hatası alıyorum

**Kontrol edin:**
1. `services.yml`'de `extra_files` tanımlı mı?
2. Kaynak dosya/dizin projede mevcut mu?
3. Log'larda hata mesajı var mı?
4. Sunucuda hedef path zaten var mı ve türü doğru mu?

**Örnek Log Kontrolü:**
```
📁 1 adet extra file/dizin kopyalanacak...
📦 Kaynak türü: dosya (default.conf)
⚠️ Sunucuda /app/myapp/default.conf dizin olarak mevcut, dosya kopyalamak için temizleniyor...
✅ dosya kopyalandı: default.conf → /app/myapp/default.conf
```

Eğer log'da mesaj görünmüyorsa, `services.yml`'de `extra_files` tanımlanmamış demektir.

---

### Q25: "not a directory" veya "Are you trying to mount a directory onto a file" hatası

**Sorun:** Sunucuda hedef path farklı türde (dosya/dizin) var ve Docker mount hatası veriyor.

**Çözüm:** Bu hata artık otomatik olarak düzeltilir. `extra_files` kopyalama işlemi sırasında:

1. Kaynak türü kontrol edilir (dosya/dizin)
2. Sunucuda hedef path'in türü kontrol edilir
3. Tür uyumsuzluğu varsa otomatik temizlenir
4. Doğru türde kopyalanır

Eğer hala hata alıyorsanız:
- Log'larda `⚠️ Sunucuda ... temizleniyor...` mesajını kontrol edin
- Manuel olarak sunucuda hedef path'i kontrol edin:
  ```bash
  ssh sistem@[IP] "ls -la /app/myapp/default.conf"
  ```

---

## 📚 Daha Fazla Bilgi

- **Fonksiyon Referansları:** [README.md](../README.md)
- **Detaylı Kullanım Kılavuzu:** [KULLANIM_KILAVUZU.md](./KULLANIM_KILAVUZU.md)
- **Gerçek Örnekler:** [Example/](../Example/)
- **CI/CD Öncesi Hazırlık:** [01-cicd-hazirliklari.md](./01-cicd-hazirliklari.md)

---

## 💡 İpucu

Sorun yaşıyorsanız önce log'ları kontrol edin. Jenkins pipeline log'larında her adım için detaylı mesajlar bulunmaktadır:

- `📁` - extra_files işlemi
- `📦` - Kaynak türü tespiti
- `⚠️` - Uyarı mesajları
- `✅` - Başarılı işlemler
- `❌` - Hata mesajları
