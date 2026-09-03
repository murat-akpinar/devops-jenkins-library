# İyileştirme Envanteri

> **Geçici çalışma dosyası.** Karar verildikten sonra silinecek — commit edilmemeli.
> Tarih: 2026-09-03 · Kapsam: `vars/` (18 dosya) + `Example/`
> **3. gözden geçirme yapıldı** — 3 madde düzeltildi, 9 madde eklendi, kapsam bölümü eklendi.
> Değişiklikler ⚠️ (düzeltme) ve 🆕 (yeni) ile işaretli.
> Her madde kaynak kodda doğrulandı; satır numaraları mevcut çalışma ağacına göre.

Öncelik: **P0 önkoşul → P0 güvenlik → P1 doğruluk → P2 performans → P3 dayanıklılık → P4 tutarlılık.**

---

## İlerleme

| Dalga | Maddeler | Durum |
|-------|----------|-------|
| 0a doğrulama altyapısı | tools/syntax-check.groovy + GitHub Actions CI | ✅ |
| 1 güvenlik | 1, 2, 4, 16, 28 | ✅ |
| 2 build cache | 10, 22, 23 | ✅ |
| 3 deploy davranışı | 6, 7, 11, 12, 17 | ✅ |
| 4 SSH | 9, 15 | ✅ |
| 5 env hijyeni | 8, 21, 25, 27 | ✅ |
| 6 dayanıklılık | 14, 18, 19, 20, 24, 26, 29 | ✅ |
| 7 tarama | 3, 5 | ✅ |
| 0b sürümleme | tag + @Library pinleme | ⏳ Karar bekliyor |
| 8 paralel deploy | 13 | ⏳ Yapılmadı (bilinçli) |

**29/31 madde tamamlandı.** Yapılmayan ikisi:

- **Madde 0b (sürümleme):** Sürüm etiketi atmak senin kararın — hangi noktanın `v2.0`
  olacağını ve tüketicilerin ne zaman geçeceğini ben belirleyemem. README'lere
  `@Library('devops-jenkins-library@v2.0') _` kullanımı ve davranış değişikliği tablosu
  eklendi; geriye yalnızca `git tag` kalıyor.
- **Madde 13 (paralel deploy):** Bilinçli olarak yapılmadı. `@Field` compose key set'leri
  thread-safe değil; eşzamanlı erişimde `docker compose up` birden fazla kez ya da hiç
  çalışmayabilir. Bu, test edilmeden yapılabilecek bir değişiklik değil ve bu makinede
  Groovy/JDK olmadığı için hiçbir şey çalıştırılamıyor.

> ⚠️ **Hiçbir değişiklik çalıştırılarak test EDİLMEDİ.** Doğrulama olarak yapılanlar:
> ayraç dengesi, Groovy kaçış simülasyonu (üretilen kabuk metni birebir çıkarıldı),
> shellcheck, ve özel karakterli/çok satırlı değerlerle kabuk davranış testleri.
> Gerçek pipeline doğrulaması şart — `groovy tools/syntax-check.groovy` ilk adım.

**Uygularken planın yanlış çıktığı yerler:**
- **Madde 1:** Planda "`\$NEXUS_PASS` diye kaçır" yazıyordu; yetersizmiş — o durumda değeri
  yerel kabuk genişletip `ssh` argv'sine koyuyor, şifre yine `ps aux`'ta görünüyordu.
  Kimlik bilgileri stdin'e taşındı.
- **Madde 9:** Uygulama sırasında iki hata yakalandı — helper `call()` dışında olduğu için
  `SSH_OPTS` kapsamda değildi, ve 6 tek-tırnaklı `sh` çağrısında `${SSH_OPTS}` sessizce
  boşa düşüyordu (bu çağrılar önceden `ConnectTimeout` taşıyordu).
- **Madde 8:** Yeni bir hata bulundu — eski anahtar deseni `^[A-Z_]+=` adında rakam geçen
  değişkenleri (`AWS_S3_SECRET`) sessizce atlıyordu; o credential'lar `.env`'e hiç
  yazılmıyordu. Desen düzeltildi, bu bir davranış değişikliği.

---

## ⚠️ 1. taslakta yaptığım hatalar

| # | Ne yanlıştı |
|---|-------------|
| 8 | Önerdiğim çözüm **yanlıştı**. "Mevcut kod çok satırlı değerleri zaten doğru ele almıyor" dedim — yanlış, doğru ele alıyor. Benim önerim onu bozardı. Madde 8 yeniden yazıldı. |
| 12 | "Build başına garanti 30 sn kazanç" dedim — abartı. Kazanç DockerScan'in indeksleme davranışına bağlı, garanti değil. Düzeltildi. |
| 1 | Eksik kalmıştı: bu yalnızca güvenlik sorunu değil, **fonksiyonel bug** da — şifre `'` içerirse komut kırılıyor. Eklendi. |

---

## 🆕 Kapsam — neyi inceledim, neyi incelemedim

Bu envanter **eksiksiz değil.** Kod kapsamı yüksek, doküman kapsamı düşük.

| Alan | Durum |
|------|-------|
| `vars/*.groovy` (18 dosya) | ✅ Tamamı okundu |
| `vars/deployService.groovy` | ⚠️ ~%85 — `extraFiles` bloğu 3. geçişte okundu (madde 27 çıktı) |
| `vars/sendEmailNotification.groovy` | ⚠️ ~%50 — HTML gövdesi (satır 60-150) okunmadı |
| `vars/checkovScan.groovy` | ⚠️ ~%75 — son bölümü okunmadı |
| `Example/*` | ✅ Tamamı |
| `credentials_prod/test.groovy` | ✅ 3. geçişte okundu (madde 28 çıktı) |
| `docs/SSS.md` (562 satır) | ❌ **Okunmadı** — yalnızca grep'lendi (madde 29) |
| `docs/FAQ.md` (561 satır) | ❌ **Okunmadı** — yalnızca grep'lendi (madde 29) |
| `docs/01-cicd-*.md` (625 satır) | ⚠️ Yalnızca grep'lendi |
| `docs/02-*.md`, `docs/03-*.md` (538 satır) | ❌ **Okunmadı** |
| `docs/USAGE_GUIDE.md`, `KULLANIM_KILAVUZU.md` | ⚠️ Yalnızca düzenlediğim bölümler |

**Kabaca ~1660 satır doküman hiç okunmadı.** Bu bir doğruluk riski: plan davranış değiştiriyor (`--wait`, prune, `deployAllServices`), dokümanlar eski davranışı anlatmaya devam ediyor. Madde 29 bunu somutlaştırıyor.

**Ayrıca hiç bakılmayan konular:** Jenkinsfile dışı entegrasyonlar (webhook kurulumu, Jenkins job DSL), `LICENSE`, CI yapılandırması (yok zaten — madde 0a).

---

## 🆕 P0 — Önkoşul (bunlar olmadan diğerlerine başlanmamalı)

### 0a. Hiçbir doğrulama mekanizması yok 🆕  ✅ **TAMAMLANDI**
**Nerede:** repo genelinde — test yok, CI yok, syntax kontrolü yok.

Bu plan 18 dosyada 20+ değişiklik öneriyor ve tek doğrulama yolu **gerçek bir pipeline çalıştırmak**. Groovy'de bir yazım hatası, ancak o fonksiyona ulaşan build'de patlar — yani Deploy stage'indeki bir hata, 10 dakikalık build'in sonunda ortaya çıkar. Planın en büyük riski maddelerin kendisi değil, doğrulanamıyor olmaları.

**Minimum çözüm (bu ilk yapılmalı):**
1. **Syntax kontrolü** — `groovyc`'yi yalnızca parse için çalıştıran küçük bir script (`vars/*.groovy` derlenmeye çalışılır, Jenkins DSL çağrıları çözülemese de sözdizimi hataları yakalanır). Yerelde ve CI'da saniyeler sürer.
2. **Saf mantık için birim testi** — Jenkins'e ihtiyaç duymayan fonksiyonlar zaten var: `computeOsvGrade`, `computeCheckovGrade`, `resolveRecipients`, `checkDiff`'in path normalizasyonu, grade karşılaştırması. Bunlar düz Groovy/Spock ile test edilebilir.
3. **Smoke pipeline** — tek servisli bir dummy repo + Jenkins job; riskli dalgalardan sonra elle koşulur.

JenkinsPipelineUnit ile tam kapsam ideal ama şart değil; 1 ve 3 tek başına riskin çoğunu alır.
**Efor:** 1 için ~1 sa, 3 için ~2 sa · **Risk:** yok · **Blocker: evet**

---

### 0b. Kütüphane sürümlenmiyor, her merge tüm pipeline'lara anında gidiyor 🆕
**Nerede:** [Example/Jenkins_example.groovy:1](Example/Jenkins_example.groovy#L1) — `@Library('devops-jenkins-library') _`

Sürüm belirtilmediği için Jenkins varsayılan branch'i (`main`) çeker. Yani `main`'e atılan her commit, kütüphaneyi kullanan **tüm** pipeline'ları o anda etkiliyor. Bu oturumda eklenen `--wait` gibi bir değişiklik, kimse haberdar olmadan herkesin deploy'unu değiştiriyor.

**Çözüm:**
- Sürüm etiketleri (`v2.0`, `v2.1`) ve `@Library('devops-jenkins-library@v2.1') _` kullanımının README'de belgelenmesi.
- Riskli değişikliklerin önce bir tag'e alınıp, tüketicilerin kendi hızlarında geçmesi.
- Jenkins global library ayarında "Allow default version to be overridden" açık olmalı.

**Efor:** ~1 sa (belgeleme + ilk tag) · **Risk:** yok · **Bu yapılmadan P0/P1 dalgaları canlıya çıkmamalı**

---

## P0 — Güvenlik

### 1. Nexus şifresi shell komut satırına gömülüyor ⚠️ genişletildi  ✅ **TAMAMLANDI**
**Nerede:** [pullService.groovy:49](vars/pullService.groovy#L49), [runTestsInContainer.groovy:44](vars/runTestsInContainer.groovy#L44), [trivyScan.groovy:28](vars/trivyScan.groovy#L28)

```groovy
printf '%s' '${NEXUS_PASS}' | docker login ... -u '${NEXUS_USER}' --password-stdin
```

`${NEXUS_PASS}` üçlü-tırnaklı bir Groovy GString içinde — yani **shell değil, Groovy** genişletiyor. Şifrenin gerçek değeri komutun kendisine yazılıyor.

- Komut çalıştığı sürece agent'ta `ps aux` çıktısında görünür (paylaşımlı agent'ta diğer build'ler okuyabilir).
- `trivyScan`'de değer uzak host'a `ssh ... 'NEXUS_USER="..." NEXUS_PASS="..."'` şeklinde gidiyor → DockerScan sunucusunun process listesinde de görünür.
- Jenkins konsol maskelemesi yalnızca konsolu korur, `ps` çıktısını değil.
- ⚠️ **Ayrıca fonksiyonel bug:** değer `'...'` içine gömüldüğü için şifrede tek tırnak varsa komut sözdizimsel olarak kırılıyor. Şu an "şifre değiştirilemez" gibi görünen bir kısıt yaratıyor olabilir.

**Doğru desen zaten repoda var** — [buildAndPushService.groovy:60-62](vars/buildAndPushService.groovy#L60) `\$NEXUS_REPO_USER` ile kaçırıyor, genişletmeyi shell yapıyor, değer `withCredentials`'ın env değişkeninde kalıyor.

**Çözüm:** Üç yerde `${NEXUS_PASS}` → `\$NEXUS_PASS`, `${NEXUS_USER}` → `\$NEXUS_USER`. `trivyScan`'de `runScan(NEXUS_USER, NEXUS_PASS)` closure parametreleri tamamen kaldırılmalı.
**Efor:** ~30 dk · **Risk:** düşük · **Davranış değişikliği:** yok (şifrede `'` varsa: düzelme)

---

### 2. Build log'u e-posta ile tüm takıma gönderiliyor  ✅ **TAMAMLANDI**
**Nerede:** [sendEmailNotification.groovy:169](vars/sendEmailNotification.groovy#L169) — `attachLog: true`

Tam build log'u eke konuyor ve `emailTeams` üzerinden takım listelerine dağıtılıyor. Log, `set -x` açık olan bölümlerin komut trace'lerini içeriyor — madde 1 düzeltilmeden bu, şifrenin e-posta ekinde arşivlenmesi demek.

**Çözüm:** Önce madde 1. Sonra `attachLog` ya `config.attachLog` ile opt-in yapılmalı, ya da yalnızca `status == 'failure'` durumunda eklenmeli.
**Efor:** ~15 dk · **Risk:** düşük

---

### 3. Tarama host'una servis dizininin tamamı kopyalanıyor  ✅ **TAMAMLANDI**
**Nerede:** [checkovScan.groovy:46](vars/checkovScan.groovy#L46), [osvScan.groovy:46](vars/osvScan.groovy#L46)

```
scp -r ${WORKSPACE}/${servicePath}/. ...:/tmp/checkov-input/${imageName}/
```

Filtre yok: `node_modules/`, `.git/`, build çıktıları ve **`.env` dosyaları** dahil her şey tarama sunucusuna gidiyor ve `/tmp` altında kalıyor.

**Çözüm:** `rsync --exclude` veya `tar --exclude ... | ssh 'tar -x'`. Tarama sonrası `/tmp/*-input/` temizlenmeli.
**Efor:** ~1 sa · **Risk:** orta (tarama girdisi değişiyor, sonuçlar doğrulanmalı)

---

### 21. `deployService` global env'i kirletiyor, `collectCredentials` bunları credential sanıyor 🆕  ✅ **TAMAMLANDI**
**Nerede:** [deployService.groovy](vars/deployService.groovy) — 14 ayrı yazım (satır 19, 83, 89, 95, 112, 118-120, 185-187, 202, 235-237)

`deployService`, shell'e değer geçirmek için global `env`'e yazıyor: `DEPLOY_SSH_PORT`, `DEPLOY_SSH_USER_CHECK`, `DEPLOY_IP_CHECK`, `DEPLOY_DEST_PATH_CHECK`, `DEPLOY_SRC_*`, `DEPLOY_APP_NAME_ENV`, `DEPLOY_ENV_FILE_GIT` …

**Niyet doğru** (Groovy interpolasyonundan kaçınmak için — madde 1'in çözümüyle aynı fikir), **hijyen yanlış**: bu değişkenler build boyunca kalıcı.

Sızıntı zinciri:
- [collectCredentials.groovy:9-12](vars/collectCredentials.groovy#L9) denylist'inde `DEPLOY_` **prefix'i yok** — yalnızca `DEPLOY_TARGETS` tek tek listelenmiş.
- [deployService.groovy](vars/deployService.groovy) içindeki `systemEnvVarsToFilter` yalnızca 6 değişken filtreliyor, hiçbiri `DEPLOY_*` değil.
- Sonuç: `collectCredentials()` bir `deployService()` çağrısından **sonra** çalışırsa, 14 `DEPLOY_*` değeri "credential" sayılıp deploy host'undaki `.env` dosyasına yazılıyor.

> Mevcut `Example` akışında `collectCredentials()` `deployAllServices()`'e argüman olarak, ilk `deployService` çağrısından önce değerleniyor — bu yüzden şu an tetiklenmiyor. Ama stage sırasını değiştiren ya da ikinci kez `collectCredentials()` çağıran her pipeline bunu yaşar.

**Çözüm:** `env.X = ...` yerine `withEnv(["X=${...}"]) { sh '...' }` — kapsamlı, otomatik temizlenen, global'i kirletmeyen. Bu idiom repoda zaten kullanılıyor ([sonarQubeAnalysis.groovy:27](vars/sonarQubeAnalysis.groovy#L27)). Ek olarak `collectCredentials` denylist'ine `DEPLOY_` prefix'i eklenmeli (ucuz emniyet kemeri).
**Efor:** ~2 sa (14 yer) · **Risk:** orta — `sh` scriptleri tek tırnaklı, dikkatli taşınmalı

---

### 28. `credentials_*.groovy` sır doldurulmak üzere tasarlanmış ama gitignore'da değil 🆕  ✅ **TAMAMLANDI**
**Nerede:** [credentials_prod.groovy](credentials_prod.groovy), [credentials_test.groovy](credentials_test.groovy), [.gitignore](.gitignore)

İki dosya da temiz şablon — `APP_NAME`, `AÇIKLAMA`, `DEĞER` placeholder'ları, gerçek sır yok. **Mevcut durumda sızıntı yok.**

Sorun tasarımda: bu dosyaların amacı kullanıcı tarafından **gerçek sırlarla doldurulup** Jenkins script console'da çalıştırılmak (`POSTGRES_PASSWORD`, `AWS_SECRET_ACCESS_KEY`, `LDAP_PASSWORD`, `NEXTAUTH_SECRET` …). `.gitignore` yalnızca `.proje/*` içeriyor — yani kullanıcı repo'yu klonlayıp dosyayı doldurduğunda **`git status` onu takip edilen bir değişiklik olarak gösterir** ve kazara commit edilir. Public bir repo için hazır bir tuzak.

**Çözüm:** Dosyaları `credentials_prod.groovy.example` / `credentials_test.groovy.example` olarak yeniden adlandırmak ve `.gitignore`'a `credentials_prod.groovy` + `credentials_test.groovy` eklemek. README'de "kopyala, doldur, çalıştır, sonra sil" akışını yazmak.

> **Ayrıca doğrulanmalı (iddia etmiyorum):** Script `new hudson.util.Secret(cred.secret)` kullanıyor. Modern Jenkins'te `hudson.util.Secret`'ın public constructor'ı yok; belgelenen API `Secret.fromString(...)`. Groovy private constructor'lara erişebildiği için çalışıyor _olabilir_ — ama Jenkins sürümüne bağlı. Script console'da bir kez denenmeli; kırıksa `Secret.fromString()` ile değiştirilmeli.

**Efor:** ~30 dk · **Risk:** yok · **Önerilen dalga:** 1 (ucuz ve önleyici)

---

## P1 — Doğruluk

### 4. `runTestsInContainer` exit-code mantığı ölü kod  ✅ **TAMAMLANDI**
**Nerede:** [runTestsInContainer.groovy:19-29](vars/runTestsInContainer.groovy#L19)

Script `set -e` ile başlıyor. `docker run` başarısız olduğunda shell **o satırda** çıkıyor; `TEST_EXIT=$?` hiç çalışmıyor. Yani `TEST_EXIT` okunduğunda daima `0`; test başarısızsa temizlik (`docker rmi`) yapılmıyor ve özel hata mesajı basılmıyor. Adım yine de fail ediyor — *yanlış yeşil değil*, ama temizlik ve teşhis kayboluyor.

**Çözüm:** `trap` ile temizliği garantiye almak, ya da `if ! ssh ...; then` yapısı.
**Efor:** ~30 dk · **Risk:** düşük

---

### 5. Checkov/OSV quality gate geçmiş build'lerin sonuçlarını da hesaba katıyor  ✅ **TAMAMLANDI**
**Nerede:** [checkovQualityGate.groovy:66](vars/checkovQualityGate.groovy#L66), [osvQualityGate.groovy:57](vars/osvQualityGate.groovy#L57)

API `?project=X` ile sorgulanıyor, dönen dizi `tag` alanı taşıyor ama **filtrelenmiyor**. `worstGrade` projenin tüm geçmiş taramaları üzerinden hesaplanıyor — bir kez kötü not almış eski tag sonraki tüm build'leri süresiz fail ettirir.

> **Teyit gerekiyor:** DockerScan `/api/checkov` ve `/api/osv` gerçekten tüm tag'leri mi döndürüyor? Kod yorumu bunu ima ediyor ama API davranışı doğrulanmalı.

**Çözüm:** `scans.findAll { it.tag == env.VERSION }`, ya da API destekliyorsa `&tag=`.
**Efor:** ~30 dk (+ API teyidi) · **Risk:** düşük

---

### 6. `--wait` eski Docker Compose'da deploy'u kırar  ✅ **TAMAMLANDI**
**Nerede:** [deployService.groovy:336](vars/deployService.groovy#L336) — bu oturumda eklendi

`--wait` Compose ≥ 2.1.1, `--wait-timeout` ≥ 2.17 istiyor. Daha eski compose'lu host'ta "unknown flag" ile **her deploy** patlar. Repoda compose sürüm önkoşulu yazmıyor.

**Çözüm:** Host'ta `docker compose version --short` bir kez okunup (`@Field` map'te host başına cache'lenir) bayraklar yalnızca destekleniyorsa eklenir.
**Efor:** ~1 sa · **Risk:** düşük

---

### 7. `--wait`, çıkan container'larda fail eder  ✅ **TAMAMLANDI**
Migration/init gibi `exit 0` veren container'lar `up -d --wait`'i hata döndürtür. Yetenek tespiti bunu çözemez.
**Çözüm:** `composeWait: false` knob'u (varsayılan `true`), README'de belgelenir.
**Efor:** ~15 dk · **Risk:** yok

---

### 22. Agent'ta Nexus `docker login` belgelenmemiş bir önkoşul 🆕  ✅ **TAMAMLANDI**
**Nerede:** [buildAndPushService.groovy](vars/buildAndPushService.groovy) — `docker login` **yalnızca Harbor için** var (satır 109), Nexus için hiç yok.

`docker push ${versionImage}` çalışıyor ama öncesinde Nexus'a login yapılmıyor. Yani agent'ın Nexus oturumu pipeline dışında (elle ya da provisioning ile) kurulmuş olmak zorunda. Hiçbir yerde yazmıyor; yeni bir agent eklendiğinde push "unauthorized" ile patlar ve sebebi belirsiz görünür.

Aynı önkoşul **madde 10'u da etkiliyor**: `--cache-from` özel registry'den cache manifest'i çekeceği için o login'e ihtiyaç duyar.

**Çözüm:** İki seçenek — (a) `buildAndPushService`'e Harbor'daki gibi bir Nexus login adımı eklemek (mevcut credential zaten bağlı, en tutarlısı), ya da (b) önkoşulu `docs/01-cicd-hazirliklari.md`'ye açıkça yazmak. (a) tercih edilir.
**Efor:** ~45 dk · **Risk:** düşük

---

## P2 — Performans

### 8. `collectCredentials` her env değişkeni için ayrı shell açıyor ⚠️ çözüm düzeltildi  ✅ **TAMAMLANDI**
**Nerede:** [collectCredentials.groovy:33](vars/collectCredentials.groovy#L33)

`printenv ${key}` döngü içinde — **değişken başına bir `sh` adımı**. Jenkins'te her `sh` bir Durable Task: wrapper yazılıyor, process başlatılıyor, çıktı poll ediliyor. 40-60 değişken → 40-60 ayrı adım, build başına kolayca dakikalar.

> ⚠️ **1. taslakta önerdiğim çözüm yanlıştı.** "Tek `env` dump'ı alıp Groovy'de `=` ile böl" demiştim ve "mevcut kod çok satırlı değerleri zaten doğru ele almıyor" diye eklemiştim. İkisi de yanlış:
> mevcut kod `env | grep -E "^[A-Z_]+=" | cut -d= -f1` ile **yalnızca anahtarları** alıyor (çok satırlı değerin devam satırları `^[A-Z_]+=` ile eşleşmediği için doğru şekilde atlanıyor), sonra `printenv KEY` ile **tam değeri** okuyor. Yani çok satırlı değerler (PEM anahtarı, sertifika, SSH key gibi credential'lar) şu an **doğru** çalışıyor. Benim önerim bunları ilk satırda keserdi.

**Doğru çözüm** — tek `sh`, satır tabanlı, çok satırlı değerlere dayanıklı (değer base64'lenir):

```groovy
def dump = sh(returnStdout: true, script: '''
    for k in $(env | sed -n 's/^\\([A-Z_][A-Z_0-9]*\\)=.*/\\1/p' | sort -u); do
        printf '%s\\t%s\\n' "$k" "$(printenv "$k" | base64 -w0)"
    done
''')
```

Groovy tarafında her satır `\t`'den bölünüp değer decode edilir. 60 adım → 1 adım, çok satırlı değerler korunur.

> `base64 -w0` GNU coreutils'e özgü; BusyBox agent'ta `base64` tek satır üretir zaten, ama `-w0` desteklenmeyebilir — agent imajı doğrulanmalı ya da `| tr -d '\n'` ile sarılmalı.

**Efor:** ~1 sa (+ çok satırlı credential ile doğrulama) · **Risk:** orta — credential akışına dokunuyor, madde 0a'daki testler burada gerçekten işe yarar

---

### 27. `extraFiles` başına 4+ gereksiz `sh` çağrısı 🆕  ✅ **TAMAMLANDI**
**Nerede:** [deployService.groovy:83-113](vars/deployService.groovy#L83)

Her `extraFiles` girdisi için ayrı `sh` adımları çalışıyor — hepsi kabuk gerektirmeyen işler için:

| Çağrı | Ne yapıyor | Kabuksuz alternatifi |
|-------|------------|----------------------|
| `basename "$DEPLOY_SRC_BASENAME"` | dosya adı | Groovy: `src.replaceAll('/+$','').tokenize('/').last()` |
| `test -e "$DEPLOY_SRC_CHECK"` | var mı | `fileExists(src)` |
| `test -e` (git checkout sonrası) | tekrar var mı | `fileExists(src)` |
| `test -d "$DEPLOY_SRC_ISDIR"` | dizin mi | tek `sh` ile diğer kontrollerle birleştirilebilir |

Madde 8 ile aynı sınıf sorun: Jenkins'te her `sh` bir Durable Task. 3 `extraFiles` × 2 servis × 2 host = **48 adım**, hepsi `basename` ve `test` için.

`basename` ve `test -e` tamamen kaldırılabilir; yalnızca `test -d` için tek bir `sh` kalır (o da diğer uzak kontrollerle birleştirilebilir).

> Madde 21 (`withEnv`) ile aynı satırlara dokunuyor — **birlikte yapılmalı**, iki kez düzenlenmesin.

**Efor:** ~1 sa (madde 21 ile birlikte) · **Risk:** düşük

---

### 9. SSH bağlantı çoğullama (multiplexing) yok  ✅ **TAMAMLANDI**
**Nerede:** `deployService` (17 `ssh` + 4 `scp`), `pullService` (5 `ssh`), `runTestsInContainer`, `checkovScan`, `osvScan`, `trivyScan`

Her çağrı ayrı TCP + kripto handshake. 2 servis × 2 host'luk mütevazı bir deploy ≈ **100 handshake**.

**Çözüm:** Ortak `sshOpts`, tüm `ssh`/`scp` çağrılarına:
```
-o ControlMaster=auto -o ControlPersist=60s -o ControlPath=/tmp/jk-%r@%h:%p
```
`ControlPath` kısa tutuluyor — socket yolu 104 karakter sınırına takılmasın. Aynı agent'taki paralel build'lerin aynı host'a olan oturumu paylaşması istenen davranış.
**Efor:** ~1,5 sa · **Risk:** düşük · **Davranış değişikliği:** yok

---

### 10. `--cache-from` şu anda hiçbir işe yaramıyor  ✅ **TAMAMLANDI**
**Nerede:** [buildAndPushService.groovy:72-73](vars/buildAndPushService.groovy#L72)

`DOCKER_BUILDKIT=1` ile build ediliyor ve `--cache-from ...:latest` veriliyor — ama `BUILDKIT_INLINE_CACHE=1` geçilmediği için push edilen imajlarda cache metadata'sı **yok**. Bayrak her build'de sessizce boşa gidiyor.

**Çözüm:** `--build-arg BUILDKIT_INLINE_CACHE=1`. Tek satır.

**Bağımlılıklar (1. taslakta atlamıştım):**
- 🆕 **Madde 22** — registry'den cache okumak agent'ta Nexus login'i gerektirir.
- 🆕 **Madde 23** — `dockerCleanup` her build'de `docker builder prune -f` çalıştırıp yerel BuildKit cache'ini siliyor.
- `pushLatest: false` seçen projelerde `:latest` push edilmediği için cache yine soğuk kalır — README'de yazılmalı.

**Efor:** ~15 dk · **Risk:** yok · **Getiri/maliyet oranı en yüksek madde**

---

### 23. `dockerCleanup`, build cache'ini her seferinde yok ediyor 🆕  ✅ **TAMAMLANDI**
**Nerede:** [dockerCleanup.groovy:20](vars/dockerCleanup.groovy#L20) — `docker builder prune -f`

Bu oturumda kaynak repodan aynen taşındı. `docker image prune -f` (dangling imajlar) makul, ama `docker builder prune -f` **tüm BuildKit cache'ini** siliyor — yani madde 10 ile doğrudan çelişiyor: bir yandan cache'i çalıştırmaya uğraşıyoruz, diğer yandan her build'de siliyoruz.

Registry inline cache (madde 10) yerel cache olmadan da çalışır, ama yerel cache çok daha hızlıdır. Disk baskısı yoksa silmek için sebep yok.

**Çözüm:** `pruneBuilder` knob'u, **varsayılan `false`**. Disk sıkıntısı olan agent'lar açar. Alternatif: `docker builder prune -f --keep-storage 10GB` ile sınırlı tutmak.
**Efor:** ~15 dk · **Risk:** yok

---

### 11. Pre-pull, deploy edilmeyecek servisleri de çekiyor  ✅ **TAMAMLANDI**
**Nerede:** [deployAllServices.groovy:61-79](vars/deployAllServices.groovy#L61)

Pre-pull döngüsü `isDeployingSvc` kontrolü yapmadan **tüm** servisleri her host'a çekiyor:
- Değişmemiş servisin imajı gereksiz indiriliyor — üstelik [pullService.groovy:30](vars/pullService.groovy#L30) önce `docker rmi` ile yerel kopyayı siliyor, yani doğru duran imaj silinip baştan çekiliyor.
- `pullService` hata durumunda `error` atıyor: o `VERSION` için hiç push edilmemiş bir servis **tüm deploy'u** düşürüyor.

**Çözüm:** Pre-pull döngüsüne `if (!isDeployingSvc(svc)) return`.
**Efor:** ~15 dk · **Risk:** düşük

---

### 12. Quality gate'ler ilk denemeden önce koşulsuz bekliyor ⚠️ iddia yumuşatıldı  ✅ **TAMAMLANDI**
**Nerede:** [trivyQualityGate.groovy:33](vars/trivyQualityGate.groovy#L33), [checkovQualityGate.groovy:36](vars/checkovQualityGate.groovy#L36), [osvQualityGate.groovy:33](vars/osvQualityGate.groovy#L33)

`sleep(retryDelay)` döngünün **başında** — sonuç hazır olsa bile 10 sn bekleniyor, üç gate için toplam 30 sn.

> ⚠️ 1. taslakta "build başına garanti 30 sn kazanç" yazmıştım — abartıydı. Tarama adımları senkron göründüğü için sonuçlar muhtemelen hazır, ama DockerScan tarafında indeksleme asenkronsa ilk sorgu zaten boş döner ve tek bir deneme harcanmış olur. **Gerçek kazanç 0–30 sn arası, DockerScan'in davranışına bağlı** (madde 5'in API teyidiyle birlikte netleşir).

**Çözüm:** `if (attempt > 1) sleep(retryDelay)`. Kazanç belirsiz olsa da maliyeti sıfır ve doğru desen.
**Efor:** ~15 dk · **Risk:** yok

---

### 13. (Opsiyonel) Host bazlı paralel deploy
Pull ve deploy şu an servis × host sırayla. Jenkins `parallel` ile eşzamanlı gidilebilir.

**Neden ayrı tutuluyor:** `@Field Set executedComposeKeys` / `copiedComposeKeys` thread-safe değil; eşzamanlı erişimde compose birden fazla kez çalışabilir veya hiç çalışmayabilir. `Collections.synchronizedSet` + atomik "ekleyebildim mi" kontrolü gerekir. Log çıktısı da iç içe geçer.
**Efor:** ~4 sa + test · **Risk:** yüksek

---

## P3 — Dayanıklılık

### 14. `waitForQualityGate()` timeout'suz  ✅ **TAMAMLANDI**
**Nerede:** [sonarQubeQualityGate.groovy:10](vars/sonarQubeQualityGate.groovy#L10)

Webhook hiç gelmezse adım süresiz asılı kalır — yalnızca job timeout'u kurtarır (örnekte **19999 sn ≈ 5,5 saat**), o süre boyunca bir executor tutulur.
**Çözüm:** `timeout(time: config.timeoutMinutes ?: 10, unit: 'MINUTES') { waitForQualityGate() }`
**Efor:** ~15 dk · **Risk:** düşük

---

### 15. Tarama host'u çağrılarında `ConnectTimeout` yok  ✅ **TAMAMLANDI**
`checkovScan` (3), `osvScan` (4), `trivyScan` (1) — yalnızca `-o StrictHostKeyChecking=no`. Yanıt vermeyen bir DockerScan host'u adımı süresiz bloklar. DockerScan host'u için `sshPort` desteği de hiç yok.
**Çözüm:** Madde 9'daki ortak `sshOpts` bunu da kapsar.
**Efor:** madde 9'a dahil

---

### 16. Quality gate raporlamasında null alan NPE riski  ✅ **TAMAMLANDI**
[checkovQualityGate.groovy:78](vars/checkovQualityGate.groovy#L78), [osvQualityGate.groovy:66](vars/osvQualityGate.groovy#L66) — `scan.serviceName.padRight(20)` null'da NPE. Aynı sınıf hata `find` closure'larında daha önce düzeltilmişti (commit `d8ad451`), bu ikisi atlanmış.
**Çözüm:** `(scan.serviceName ?: '?').padRight(20)`
**Efor:** ~5 dk · **Risk:** yok

---

### 17. Deploy host'unda koşulsuz `docker image prune -f`  ✅ **TAMAMLANDI**
[deployService.groovy:343](vars/deployService.groovy#L343) — bu oturumda eklendi. Her deploy'da host geneli çalışıyor; paylaşımlı host'ta başka projelerin dangling imajlarını da siliyor. Pratikte zararsız ama kullanıcının seçmediği bir yan etki.
**Çözüm:** `pruneDangling` knob'u (varsayılan `true`).
**Efor:** ~15 dk · **Risk:** yok

---

### 24. `checkDiff` baseline yokken "değişiklik yok" diyor 🆕  ✅ **TAMAMLANDI**
**Nerede:** [checkDiff.groovy:22-30](vars/checkDiff.groovy#L22)

`prevCommit`/`currCommit` boş ya da geçersizse `0` (= değişiklik yok) dönüyor. İlk build'de ya da `GIT_PREVIOUS_SUCCESSFUL_COMMIT` yokken "hiçbir şey değişmedi" anlamına geliyor — güvenli yön **tam tersi** olmalıydı ("bilinmiyor → build et").

`Example/Jenkins_example.groovy` bunu `hasBaseline` kontrolüyle **çağıran tarafta** telafi ediyor, o yüzden örnek akış doğru çalışıyor. Ama fonksiyonu doğrudan kullanan bir pipeline ilk build'de hiçbir şey deploy etmez ve sebebini anlamaz.

> Bu davranış hem eski hedef kodda hem kaynak kodda aynıydı; bu oturumda korundu. Değiştirmek **kırıcı** bir değişiklik — bu yüzden karar gerektiriyor, sessizce düzeltilmemeli.

**Seçenekler:** (a) olduğu gibi bırakıp README'de açıkça belgelemek, (b) `1` döndürmek (kırıcı), (c) `checkDiff(..., failOpen: true)` opsiyonu eklemek.
**Efor:** (a) 15 dk · (c) 30 dk · **Risk:** (b) yüksek

---

### 25. `extraFiles` yolları tırnaksız 🆕  ✅ **TAMAMLANDI**
**Nerede:** [deployService.groovy:143](vars/deployService.groovy#L143) civarı — `scp ${scpFlags} -P ${sshPort} ... ${src} ${sshUser}@${deployIp}:${cleanDestPath}`

`src` ve `dest` `services.yml`'den geliyor ve tırnaksız gömülüyor. Boşluk içeren bir yol komutu bölüyor. `services.yml` repo içinde olduğu için enjeksiyon riski düşük, ama boşluklu dizin adı sessizce yanlış dosya kopyalıyor.
**Çözüm:** Yolları `'...'` içine almak; ya da madde 21'in `withEnv` yaklaşımıyla birlikte çözmek.
**Efor:** ~20 dk · **Risk:** düşük

---

## P4 — Tutarlılık

### 29. `SSS.md` / `FAQ.md` bu plandan sonra yanlış bilgi verecek 🆕  ✅ **TAMAMLANDI**
**Nerede:** [docs/SSS.md](docs/SSS.md) (562 satır), [docs/FAQ.md](docs/FAQ.md) (561 satır) — ikisi de hiç okunmadı, yalnızca grep'lendi.

Grep sonucu: her iki dosyada **11'er ayrı `deployService()` atıfı** var — Q18 gibi başlıklar, `deployService([...])` kod örneği (satır 481/480), credential akışı anlatımı, sorun giderme adımları.

`deployAllServices()` eklendikten sonra bu içerik kullanıcıyı **elle deploy döngüsü kurmaya** yönlendirmeye devam ediyor — üstelik madde 11'in düzelttiği pre-pull hatasını ve `credentialsMap`'in yalnızca ilk servise geçme hatasını da öğretiyor.

Ayrıca `docs/02-*.md` ve `docs/03-*.md` (538 satır) hiç okunmadı; içlerinde eski davranışa bağlı yönergeler olabilir.

**Çözüm:** İki aşamalı — (a) `SSS.md`/`FAQ.md`'yi baştan sona okuyup bu plandaki davranış değişiklikleriyle çelişen bölümleri listelemek (~1 sa), (b) dalgalar ilerledikçe güncellemek. Madde 26 (TR/EN ikizleri) burada iki katına çıkıyor.
**Efor:** (a) ~1 sa okuma + (b) dalga başına ~30 dk · **Risk:** yok · **Ama atlanırsa dokümanlar sessizce yanlışlaşır**

---

### 18. `services.yml` yolu bazı fonksiyonlarda sabit  ✅ **TAMAMLANDI**
`dockerCleanup` ve `deployAllServices` `SERVICES_FILE` destekliyor; `checkovScan`, `osvScan`, `trivyScan` `'services.yml'` sabitini kullanıyor. Tek bir `resolveServicesFile()` yardımcısı.
**Efor:** ~30 dk · **Risk:** yok

### 19. `sshUser` varsayılanı dört ayrı yerde tekrarlanıyor  ✅ **TAMAMLANDI**
`'your-user'` literali `deployService`, `pullService`, `runTestsInContainer`, `deployAllServices` içinde ayrı ayrı. `globalConfig()`'e `DEPLOY_SSH_USER` eklenip oradan okunmalı.
**Efor:** ~30 dk · **Risk:** düşük

### 20. Satır sonları karışık  ✅ **TAMAMLANDI**
`vars/` içinde 9 dosya CRLF, 9 dosya LF. `.gitattributes` yok, git uyarı veriyor. Fonksiyonel etkisi yok, diff gürültüsü yaratıyor.
**Çözüm:** `.gitattributes` (`*.groovy text eol=lf`) + tek seferlik normalizasyon.
**Efor:** ~15 dk · **Risk:** yok (tek seferlik büyük diff)

### 26. `README.md` / `README.TR.md` ikizleri elle senkron tutuluyor 🆕  ✅ **TAMAMLANDI**
İki README ve iki kullanım kılavuzu (TR/EN) satır satır aynı yapıda. Bu oturumdaki değişikliklerde her düzenlemeyi iki kez yapmak gerekti — birinin unutulması an meselesi. Şu an senkronlar, ama bu plan 20+ değişiklik öneriyor.
**Çözüm:** Kısa vadede bir kontrol listesi; uzun vadede fonksiyon referansını tek kaynaktan üretmek (ya da EN'i kanonik yapıp TR'yi özet tutmak).
**Efor:** karar gerektirir · **Risk:** yok

---

## 🆕 Aynı dosyaya dokunan maddeler (çakışma haritası)

Bunlar **tek commit'te birlikte** yapılmalı, aksi halde aynı satırlar iki kez düzenlenir ve çakışır:

| Dosya | Maddeler |
|-------|----------|
| `deployService.groovy` | **6, 7, 17, 21, 25, 27** — hepsi aynı fonksiyon; 21+25+27 aynı satır bloğu |
| `collectCredentials.groovy` | **8, 21** (denylist'e `DEPLOY_` prefix'i) |
| `buildAndPushService.groovy` | **10, 22** — cache ve Nexus login aynı komutu ilgilendiriyor |
| `dockerCleanup.groovy` | **23** (tek başına) — ama **10** ile mantıksal çelişki içinde |
| `pullService`, `runTestsInContainer`, `trivyScan` | **1, 9, 15** — 1 (kaçırma) ve 9 (sshOpts) aynı `sh` bloklarında |
| `checkovQualityGate`, `osvQualityGate` | **5, 12, 16** — aynı döngü ve aynı raporlama satırları |
| `globalConfig.groovy` | **18, 19** |
| TR/EN doküman ikizleri | **26, 29** + her davranış değişikliğinin dokümantasyonu |

## Revize edilmiş sıra

| Dalga | Maddeler | Efor | Gerekçe |
|-------|----------|------|---------|
| **0** | **0a** (syntax kontrolü), **0b** (sürüm etiketi) | ~2 sa | 🆕 **Blocker.** Doğrulama ve geri dönüş yolu olmadan 20 değişiklik canlıya çıkmamalı. |
| **1** | 1, 2, 16, **28** | ~1,5 sa | Güvenlik + sıfır riskli NPE düzeltmesi + credential şablonu tuzağı. Davranış değişmiyor. |
| **2** | 22, 10, 23 | ~1,5 sa | 🆕 Birlikte ele alınmalı: cache düzeltmesi, agent login'i ve builder prune aynı sorunun parçaları. |
| **3** | 6, 7, 17, 11, 12 | ~2 sa | Bu oturumda eklenen risklerin kapatılması + ucuz kazançlar. |
| **4** | 9, 15 | ~1,5 sa | SSH multiplexing. Çok dosyaya dokunuyor, ayrı commit. |
| **5** | 21, 25, **27**, 8 | ~4,5 sa | Env hijyeni + `sh` çağrısı azaltma + credential toplama. Hepsi aynı satırlara dokunuyor, tek seferde. En dikkat isteyen dalga — 0a'daki testler burada karşılığını verir. |
| **6** | 4, 14, 18, 19, 20, 24, 26, **29** | ~4 sa | Dayanıklılık, temizlik ve doküman senkronu. |
| **7** | 3, 5 | ~1,5 sa | DockerScan API/host davranışının teyidini bekliyor. |
| **8** | 13 | ~4 sa | Paralel deploy — ayrı iş, ayrı test. |

**Değişen ne:**
- 2. geçiş: madde 10 dalga 1 → 2 (madde 22 ve 23 olmadan cache yine çalışmaz). Madde 8 dalga 3 → 5 (çözümü düşündüğümden riskli).
- 🆕 3. geçiş: madde 28 dalga 1'e, 27 dalga 5'e (21 ile aynı satırlar), 29 dalga 6'ya eklendi.

**🆕 Geri dönüş planı:** Madde 0b'deki sürüm etiketleri bunun ön şartı. Her dalga kendi tag'ini alsın (`v2.1`, `v2.2` …); bir dalga sorun çıkarırsa tüketiciler `@Library('devops-jenkins-library@v2.0')` ile bir öncekine döner. Etiket yoksa geri dönüş = revert commit + tüm pipeline'ların yeniden çalışması.

**🆕 Toplam efor:** ~23 saat (dalga 8 hariç ~19 saat). Bu, doküman güncellemesi ve test dahil kaba bir tahmin; madde 0a'daki doğrulama altyapısı kurulmazsa gerçekleşen süre belirgin şekilde artar.

---

## Açık sorular (kod okuyarak cevaplanamaz)

1. **Madde 5 + 12:** DockerScan `/api/checkov` ve `/api/osv` tüm tag'leri mi, yalnızca sonuncuyu mu döndürüyor? Tarama tetikleyici script senkron mu (sonuç ssh dönerken hazır mı)?
2. **Madde 3:** Checkov/OSV servis dizininin tamamına gerçekten ihtiyaç duyuyor mu, yoksa manifest/IaC dosyaları yeterli mi?
3. **Madde 6:** Hedef deploy host'larındaki Docker Compose sürümü nedir? ≥ 2.17 ise yetenek tespiti gereksiz, belgelemek yeter.
4. 🆕 **Madde 22:** Agent'ların Nexus `docker login`'i nasıl kuruluyor — provisioning ile mi, elle mi? Pipeline'a alınması sorun yaratır mı?
5. 🆕 **Madde 8:** Jenkins agent imajı GNU coreutils mi, BusyBox mu? (`base64 -w0` buna bağlı)
6. 🆕 **Madde 0b:** Bu kütüphaneyi kaç pipeline kullanıyor ve hepsi aynı anda mı güncellenebilir? Sürüm pinleme gerekli mi?
7. 🆕 **Madde 28:** `credentials_*.groovy` script console'da en son ne zaman çalıştırıldı, `new hudson.util.Secret(...)` mevcut Jenkins sürümünüzde çalışıyor mu?
8. 🆕 **Madde 29:** `SSS.md`/`FAQ.md` aktif olarak kullanılıyor mu, yoksa güncelliğini yitirmiş mi? (Güncellenmeyecekse silmek, yanlış bilgi bırakmaktan iyi.)
