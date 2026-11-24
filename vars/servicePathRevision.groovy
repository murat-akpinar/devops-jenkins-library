// Bir servis path'i için "bu servisin kaynağı şu an hangi commit'te?" sorusunun cevabı.
//   - path bir submodule ise  → submodule'ün KENDİ HEAD'i
//   - normal bir dizin ise    → superproject HEAD'i
//
// `imageRevisionChanged` ile AYNI mantığı kullanmak zorunludur: build sırasında imaja
// basılan label ile diff sırasında karşılaştırılan değer aynı kaynaktan gelmezse
// karşılaştırma sessizce anlamsızlaşır — her build "değişti" der ve atlama çalışmaz.
// Bu yüzden iki fonksiyon aynı yardımcıyı paylaşıyor, mantık kopyalanmıyor.
//
// Kullanım (build aşaması):
//   buildAndPushService([
//       // ...
//       labels: ['org.opencontainers.image.revision': servicePathRevision(svc.path)]
//   ])
def call(String path) {
    def trimmed = path?.toString()?.trim()
    if (!trimmed) {
        return null
    }
    // Submodule tespiti ve rev-parse TEK `sh` adiminda: buildAndPushService bunu her
    // build'de her servis icin cagiriyor, iki ayri adim Jenkins'te gereksiz yuk.
    // Checkout edilmiş bir submodule'de '<path>/.git' bir DOSYADIR (gitlink), dizin değil.
    // Yol withEnv ile gecirilir, komut dizesine GOMULMEZ: bosluk ya da tirnak iceren
    // bir servis yolu komutu bolmez.
    def sha = ''
    withEnv(["SPR_PATH=${trimmed}"]) {
        sha = sh(
            script: '''
                if [ -f "$SPR_PATH/.git" ]; then
                    git -C "$SPR_PATH" rev-parse HEAD 2>/dev/null || true
                else
                    git rev-parse HEAD 2>/dev/null || true
                fi
            ''',
            returnStdout: true
        ).trim()
    }
    return sha ?: null
}
