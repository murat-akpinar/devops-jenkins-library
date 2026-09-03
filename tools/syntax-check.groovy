// vars/*.groovy sozdizimi kontrolu.
//
// Neden gerekli: bu kutuphanede test yok ve tek dogrulama yolu gercek bir pipeline
// calistirmakti. Groovy'de bir yazim hatasi ancak o fonksiyona ULASAN build'de patliyor —
// Deploy stage'indeki bir hata 10 dakikalik build'in sonunda ortaya cikiyor.
//
// Nasil calisir: derleme yalnizca CONVERSION fazina kadar yurutulur (lexer + parser + AST).
// Sinif cozumleme yapilmadigi icin Jenkins DSL cagrilari (sh, echo, withCredentials, readYaml…)
// "cozulemeyen sinif" hatasi vermez; buna karsilik gercek sozdizimi hatalari yakalanir:
// kapanmamis ayrac, hatali string kacisi, bozuk closure, eksik parantez.
//
// Kullanim:  groovy tools/syntax-check.groovy
// Cikis kodu: hatasizsa 0, en az bir dosya bozuksa 1.

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
