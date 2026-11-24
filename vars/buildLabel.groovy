// Build'in Jenkins arayüzündeki adını ve açıklamasını yazar.
//
// Neden: Jenkins'in sol sütunundaki Build History listesi varsayılan olarak yalnız "#42"
// gösteriyor. Hangi sürümün deploy edildiğini görmek için her build'e tek tek girmek
// gerekiyordu; bir sorun çıktığında "hangi tag canlıdaydı" sorusu job geçmişinden
// okunamıyordu. Bu adım listeyi "#42 prod-v1.2" haline getiriyor.
//
// Neden kütüphanede: aynı üç satır onlarca Jenkinsfile'a kopyalanırsa etiket biçimini
// değiştirmek yine toplu değişiklik demek. Biçim burada tek yerden yönetiliyor.
//
// Çağrı yeri önemli: Initialize aşamasının BAŞINA, build'i durdurabilecek `error`
// kontrollerinden ÖNCE konmalı. Sonrasına konursa o kontrolden düşen build etiketsiz
// (#42) kalır — yani etiketin en çok gerektiği durumda yazılmaz.
//
// Kullanım:
//   buildLabel()                                  // "#42 prod-v1.2" + açıklama: a1b2c3d
//   buildLabel(branch: 'main')                    // açıklama: main · a1b2c3d
//   buildLabel(version: 'hotfix-3', sha: fullSha) // her şeyi elle ver
def call(Map config = [:]) {
    // VERSION environment{} bloğunda tanımlıysa Initialize anında çözülmüş durumdadır.
    def version = (config.version ?: env.VERSION ?: '').toString().trim()
    currentBuild.displayName = version ? "#${env.BUILD_NUMBER} ${version}" : "#${env.BUILD_NUMBER}"

    // Açıklama ikinci satırda görünür. Commit zaten imaja label olarak basılıyor; burada
    // ek bir `git rev-parse` çağırmamak için yalnız env.GIT_COMMIT'e bakılıyor.
    def parts = []
    def branch = (config.branch ?: '').toString().trim()
    if (branch) { parts << branch.replaceFirst('^origin/', '') }
    def sha = (config.sha ?: env.GIT_COMMIT ?: '').toString().trim()
    if (sha && sha != 'unknown') { parts << sha.take(7) }
    if (parts) { currentBuild.description = parts.join(' · ') }
}
