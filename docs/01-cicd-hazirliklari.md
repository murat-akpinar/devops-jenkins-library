# CI/CD Pipeline Kurulum Öncesi Hazırlık Rehberi

Bu rehber, Jenkins CI/CD pipeline'ını kurmadan **önce** yapılması gereken tüm hazırlık adımlarını içerir. İlk defa CI/CD kurulumu yapacak kullanıcılar için hazırlanmıştır.

---

## 📋 Genel Bakış

CI/CD pipeline'ının çalışabilmesi için aşağıdaki hazırlıkların yapılması gerekmektedir:

1. ✅ **Network İzinleri** - Sunucular arası ağ bağlantılarının açılması
2. ✅ **Docker Registry Ayarları** - Docker'ın insecure registry'leri kullanabilmesi
3. ✅ **Nexus Bağlantısı** - Container registry'ye erişim
4. ✅ **SSH Anahtarı Kurulumu** - Sunucular arası güvenli bağlantı

---

## 🔐 1. Network İzinleri (Network To Network Talep)

CI/CD pipeline'ının çalışabilmesi için uygulama sunucusunun belirli sunuculara ve portlara erişmesi gerekmektedir.

### Adım 1: Network ekibine talep gönderin

Aşağıdaki örnek mesajı kullanarak network ekibine talep oluşturabilirsiniz:

```
Merhaba,

CI/CD süreçleri için [UYGULAMA-IP] sunucusunun Jenkins, Nexus ve Harbor sunucularına erişmesi gerekmektedir. 
Jenkins agent sunucusu SSH üzerinden dosyaları uygulama sunucusuna aktarıyor.

Gerekli network izinleri:

JENKINS_AGENT_IP > [UYGULAMA-IP] PORT 22
[UYGULAMA-IP] > NEXUS_IP PORT 8090
[UYGULAMA-IP] > JENKINS_AGENT_IP PORT 7000


Teşekkürler.
```

> **Not:** `[UYGULAMA-IP]` kısmını gerçek uygulama sunucusu IP adresi ile değiştirin.

### Adım 2: İzinlerin açılmasını bekleyin

Network ekibi izinleri açtıktan sonra test edebilirsiniz:

```bash
# Uygulama sunucusundan test edin
telnet NEXUS_IP 8090          # Nexus bağlantısı
telnet JENKINS_AGENT_IP 7000  # Jenkins Registry bağlantısı
```

---

## 🐳 2. Docker Insecure Registry Ayarları

Uygulama sunucusunda Docker'ın internal registry'lere (Nexus, Harbor) bağlanabilmesi için `insecure-registries` ayarının yapılması gerekmektedir.

### Adım 1: Docker daemon.json dosyasını kontrol edin

```bash
# Uygulama sunucusuna SSH ile bağlanın
ssh sistem@[UYGULAMA-IP]

# Docker daemon.json dosyasının varlığını kontrol edin
cat /etc/docker/daemon.json
```

### Adım 2: daemon.json dosyasını düzenleyin

Eğer dosya yoksa oluşturun, varsa içine `insecure-registries` ayarını ekleyin:

```bash
# Root yetkisi ile düzenleyin
sudo nano /etc/docker/daemon.json
```

Dosya içeriği şu şekilde olmalıdır:

```json
{
"diğer-ayarlar": "diğer-ayarlar",
"insecure-registries" : [ "NEXUS_IP:8081", "NEXUS_IP:8082", "YOUR_HARBOR_IP" ]
}
```

> **Not:** Eğer dosyada başka ayarlar varsa, sadece `insecure-registries` kısmını ekleyin ve virgülü unutmayın.

### Adım 3: Docker servisini yeniden başlatın

```bash
# Docker servisini restart edin
sudo systemctl restart docker.service

# Servisin başarıyla başladığını kontrol edin
sudo systemctl status docker.service
```

### Adım 4: Ayarların uygulandığını doğrulayın

```bash
# Docker info komutu ile insecure registry'lerin eklendiğini kontrol edin
docker info | grep -A 5 "Insecure Registries"
```

---

## 📦 3. Nexus Container Registry Bağlantısı

Uygulama sunucusunun Nexus container registry'ye erişebilmesi için login işlemi yapılmalıdır.

### Adım 1: Nexus'a login olun

Uygulama sunucusunda `sistem` kullanıcısı ile login olun:

```bash
# Uygulama sunucusunda
docker login NEXUS_IP:8082
```

İstendiğinde aşağıdaki bilgileri girin:
- **Username:** `your-nexus-user`
- **Password:** `your-nexus-password`

---

## 🔑 4. SSH Anahtarı Kurulumu ve Bağlantı Testi

Jenkins agent sunucusunun uygulama sunucusuna SSH üzerinden bağlanabilmesi için anahtar tabanlı kimlik doğrulama kurulmalıdır.

### Adım 1: Jenkins Agent sunucusuna bağlanın

```bash
# Jenkins agent sunucusuna bağlanın
ssh [kullanıcı]@jenkins-agent-hostname

# Root kullanıcısına geçin
sudo -i

# Jenkins kullanıcısına geçin (Jenkins bu kullanıcı üzerinden çalışıyor)
su - jenkins
```

### Adım 2: SSH anahtarını kopyalayın

```bash
# Jenkins kullanıcısı ile uygulama sunucusuna SSH anahtarını kopyalayın
ssh-copy-id sistem@[UYGULAMA-IP]
```

> **Not:** İlk kez bağlanıyorsanız "Are you sure you want to continue connecting (yes/no)?" sorusuna `yes` yazın.

İstenirse `sistem` kullanıcısının şifresini girin.

### Adım 3: İlk bağlantıyı test edin (Known Hosts)

SSH anahtarı kopyalandıktan sonra **mutlaka** en az bir kez manuel bağlanın. Böylece uygulama sunucusu "known hosts" listesine eklenir:

```bash
# Uygulama sunucusuna bağlanın
ssh sistem@[UYGULAMA-IP]

# Bağlantı başarılı olduysa logout olun
exit
```

> **⚠️ Önemli:** Eğer ilk bağlantıyı yapmazsanız, Jenkins pipeline'ı çalıştığında "Host key verification failed" hatası alırsınız.

### Adım 4: Şifresiz bağlantıyı doğrulayın

```bash
# Tekrar bağlanmayı deneyin, şifre sorulmamalı
ssh sistem@[UYGULAMA-IP]

# Herhangi bir komut çalıştırıp test edin
ssh sistem@[UYGULAMA-IP] "whoami"
# Çıktı: sistem

# Logout olun
exit
```

---

## 🔑 5. Jenkins Credentials Tanımlama

Pipeline'ın Nexus ve Harbor'a erişebilmesi için Jenkins'te aşağıdaki credential'lar oluşturulmalıdır.

**Manage Jenkins** → **Credentials** → **System** → **Global credentials** → **Add Credentials**

| ID | Tür | Kullanıldığı yer |
|----|-----|-----------------|
| `Nexus_Credentials` | Username with password | Docker image push/pull (Nexus) |
| `Harbor_Credentials` | Username with password | Docker image push/pull (Harbor) |

**Her credential için:**
1. **Kind**: `Username with password`
2. **Scope**: `Global`
3. **Username**: Registry kullanıcı adı
4. **Password**: Registry şifresi
5. **ID**: `Nexus_Credentials` veya `Harbor_Credentials` (tam olarak bu şekilde — büyük/küçük harf duyarlı)
6. **Save** tıklayın

---

## ✅ Kurulum Kontrol Listesi

Aşağıdaki kontrol listesini kullanarak tüm adımların tamamlandığından emin olun:

- [ ] Network izinleri talep edildi ve açıldı
- [ ] Docker `daemon.json` dosyası düzenlendi ve `insecure-registries` eklendi
- [ ] Docker servisi restart edildi ve ayarlar uygulandı
- [ ] Nexus registry'ye login yapıldı ve test edildi
- [ ] Jenkins agent'tan uygulama sunucusuna SSH anahtarı kopyalandı
- [ ] İlk SSH bağlantısı yapıldı (known hosts)
- [ ] Şifresiz SSH bağlantısı test edildi
- [ ] Jenkins'te `Nexus_Credentials` credential'ı oluşturuldu
- [ ] Jenkins'te `Harbor_Credentials` credential'ı oluşturuldu

---

## 🚀 Sonraki Adım

Tüm hazırlık adımları tamamlandıktan sonra, CI/CD pipeline kurulumuna geçebilirsiniz:

1. `Jenkinsfile` oluşturma
2. `services.yml` dosyası oluşturma
3. Jenkins'te pipeline job oluşturma

Detaylı bilgi için [`02-pipeline-kurulumu.md`](./02-pipeline-kurulumu.md) ve [`KULLANIM_KILAVUZU.md`](./KULLANIM_KILAVUZU.md) dosyalarına bakın.
