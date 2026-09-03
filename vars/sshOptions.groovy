// Tüm ssh/scp çağrılarında kullanılan ortak seçenekler.
//
// Neden: deployService tek çağrıda 17 ssh + 4 scp, pullService 5 ssh açıyordu ve her biri
// ayrı TCP + kripto handshake demekti. 2 servis × 2 host'luk mütevazı bir deploy ~100
// handshake üretiyordu. ControlMaster ile host başına tek oturum açılıp yeniden kullanılıyor.
//
// ControlPath bilerek kısa tutuldu: unix socket yolu ~104 karakterle sınırlı ve uzun workspace
// yolları bu sınırı aşıyor. %r@%h:%p sayesinde aynı agent'taki farklı build'ler aynı hedefe
// olan oturumu paylaşır — istenen davranış budur.
//
// ConnectTimeout tek yerden yönetilir; önceden checkovScan/osvScan/trivyScan'de hiç yoktu ve
// yanıt vermeyen bir host adımı süresiz blokluyordu.
def call(Map config = [:]) {
    def connectTimeout = config.connectTimeout ?: 20
    // multiplex: false → yalnızca timeout uygulanır (sorun giderme / eski OpenSSH için kaçış kapısı)
    def multiplex = (config.multiplex != null) ? (config.multiplex.toString() == 'true') : true

    def opts = "-o ConnectTimeout=${connectTimeout}"
    if (multiplex) {
        opts += " -o ControlMaster=auto -o ControlPersist=60s -o ControlPath=/tmp/jk-%r@%h:%p"
    }
    return opts
}
