// Nexus'ta PUSH EDİLMİŞ bir imajın tek bir label değerini okur.
//
// NİÇİN VAR: `git submodule update --remote` kullanan bir pipeline'da superproject'in
// gitlink pointer'ı bump'lanmadığı için "bu tag'i taşıyan imaj hangi commit'ten build
// edildi?" sorusunun cevabı git'te YOKTUR. GIT_PREVIOUS_SUCCESSFUL_COMMIT'teki pinlenmiş
// SHA bayat kalır, submodule HEAD'i ise her gün ilerler — ikisi asla eşit olmaz ve diff
// kontrolü her build'de "değişti" der, yani hiç çalışmaz.
//
// Çözüm: buildAndPushService imaja bu bilgiyi `labels` ile basar, bu fonksiyon da bir
// sonraki build'de aynı bilgiyi registry'den geri okur. Baseline böylece agent'tan,
// workspace'ten ve cleanWs'ten bağımsız, deterministik bir yerde durur.
//
// DEĞER BULUNAMAZSA null döner: tag yok, label basılmamış (ör. bu özellikten önce build
// edilmiş imajlar), registry erişilemiyor ya da manifest beklenen formatta değil.
// Çağıran taraf null'ı "bilinmiyor" kabul edip GÜVENLİ TARAFTA kalmalı, yani build etmeli.
//
// Kullanım:
//   def builtSha = imageLabelOnNexus('my-service', env.VERSION,
//                                    'org.opencontainers.image.revision',
//                                    env.NEXUS_REGISTRY_URL, env.REGISTRY_PATH)
// nexusRegistryUrl: Docker connector adresi (NEXUS_REGISTRY_URL, örn. 8090) — REST API portu DEĞİL.
def call(String serviceName, String version, String labelKey, String nexusRegistryUrl, String registryPath, String credentialId = null) {
    if (!serviceName?.trim() || !version?.trim() || !labelKey?.trim()) {
        return null
    }

    // Docker connector üzerinden Registry v2 API. buildAndPushService imajı
    // "<registry>/repository/<repo>/<servis>" adıyla push ettiği için path birebir uyuşur.
    def imageBase = "${nexusRegistryUrl}/v2/repository/${registryPath}/${serviceName}"

    // JSON ayrıştırma bilinçli olarak jq/python'a bağımlı DEĞİL — agent'ta bunların
    // bulunduğu garanti edilemez. İki basit varsayıma dayanır:
    //  1) Manifest'teki İLK sha256:<64hex> config blob'un digest'idir. Docker v2 ve OCI
    //     manifest şemalarında `config` alanı `layers`'tan önce gelir.
    //  2) Label satırı `tr ',{}'` ile tek satıra düşürülüp son ':'den sonrası alınır.
    // İkisinden biri tutmazsa çıktı boş kalır ve fonksiyon null döner (fail-open).
    //
    // grep deseni `"<key>":` olarak DARALTILDI. Düz `grep -F "<key>"` imajın history
    // kaydındaki `"created_by":"LABEL <key>=<deger>"` satırını da yakalıyor; config.Labels
    // her zaman önce geldiği için head -1 bugün doğru satırı seçse de serializer alan
    // sırasını değiştirdiğinde sessizce bozuk bir değer dönerdi.
    //
    // Script HER ZAMAN 0 ile çıkar: `set -e` + eksik tag kombinasyonunda sh exception
    // atıyor, bu da "tag henüz yok" gibi tamamen normal bir durumu build hatasına
    // çeviriyordu. Bulunamayan değer boş çıktı olarak ifade edilir.
    def script = '''
        _fetch() {
            if [ -n "${NEXUS_USER:-}" ]; then
                curl -sf -u "$NEXUS_USER:$NEXUS_PASS" "$@"
            else
                curl -sf "$@"
            fi
        }

        CFG_DIGEST=$(_fetch \\
            -H 'Accept: application/vnd.docker.distribution.manifest.v2+json' \\
            -H 'Accept: application/vnd.oci.image.manifest.v1+json' \\
            "$IMAGE_BASE/manifests/$IMAGE_TAG" 2>/dev/null \\
            | grep -o 'sha256:[0-9a-f]\\{64\\}' | head -1)

        if [ -n "$CFG_DIGEST" ]; then
            _fetch "$IMAGE_BASE/blobs/$CFG_DIGEST" 2>/dev/null \\
                | tr ',{}' '\\n\\n\\n' \\
                | grep -F -- '"'"$LABEL_KEY"'":' \\
                | head -1 \\
                | sed -e 's/.*://' -e 's/[^A-Za-z0-9._+-]//g'
        fi
        exit 0
    '''

    def readLabel = { ->
        sh(script: script, returnStdout: true)?.trim()
    }

    // ÖNCE anonim denenir: anonim erişim açıksa ve Nexus'un docker connector'ü Bearer realm ile yapılandırılmışsa `curl -u`
    // (Basic) 401 alır, yani doğrudan kimlikli sorguya geçmek çalışan bir yolu bozar.
    def value = null
    withEnv(["IMAGE_BASE=${imageBase}", "IMAGE_TAG=${version}", "LABEL_KEY=${labelKey}"]) {
        value = readLabel()

        if (!value) {
            def credId = (credentialId != null) ? credentialId : globalConfig().NEXUS_CREDENTIAL_ID
            if (credId?.trim()) {
                withCredentials([usernamePassword(
                    credentialsId: credId,
                    usernameVariable: 'NEXUS_USER',
                    passwordVariable: 'NEXUS_PASS'
                )]) {
                    value = readLabel()
                }
            }
        }
    }

    return value ?: null
}
