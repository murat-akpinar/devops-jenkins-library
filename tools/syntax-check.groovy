// vars/*.groovy sözdizimi kontrolü.
//
// Neden gerekli: bu kütüphanede test yok ve tek doğrulama yolu gerçek bir pipeline
// çalıştırmaktı. Groovy'de bir yazım hatası ancak o fonksiyona ULAŞAN build'de patlıyor —
// Deploy stage'indeki bir hata 10 dakikalık build'in sonunda ortaya çıkıyor.
//
// Nasıl çalışır: derleme yalnızca CONVERSION fazına kadar yürütülür (lexer + parser + AST).
// Sınıf çözümleme yapılmadığı için Jenkins DSL çağrıları (sh, echo, withCredentials, readYaml…)
// "çözülemeyen sınıf" hatası vermez; buna karşılık gerçek sözdizimi hataları yakalanır:
// kapanmamış ayraç, hatalı string kaçışı, bozuk closure, eksik parantez.
//
// Kullanım:  groovy tools/syntax-check.groovy
// Çıkış kodu: hatasızsa 0, en az bir dosya bozuksa 1.

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases

def root = new File(System.getProperty('user.dir'))
def varsDir = new File(root, 'vars')

if (!varsDir.isDirectory()) {
    System.err.println "HATA: vars/ dizini bulunamadi. Scripti repo kokunden calistirin."
    System.exit 2
}

def files = varsDir.listFiles().findAll { it.name.endsWith('.groovy') }.sort { it.name }
if (!files) {
    System.err.println "HATA: vars/ icinde .groovy dosyasi yok."
    System.exit 2
}

int failed = 0
println "Sozdizimi kontrolu — ${files.size()} dosya\n"

files.each { f ->
    try {
        def cu = new CompilationUnit()
        cu.addSource(f)
        cu.compile(Phases.CONVERSION)
        println "  OK    ${f.name}"
    } catch (Throwable e) {
        failed++
        println "  FAIL  ${f.name}"
        (e.message ?: e.toString()).readLines().take(8).each { println "        ${it}" }
    }
}

println ""
if (failed) {
    println "${failed}/${files.size()} dosyada sozdizimi hatasi var."
    System.exit 1
}
println "Tum dosyalar gecti."
