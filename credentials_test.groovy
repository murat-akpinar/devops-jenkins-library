import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import com.cloudbees.plugins.credentials.CredentialsStore

// Domain oluştur veya mevcut olanı al
def domain = new Domain("APP_NAME", "APP_NAME TEST ortamı credential'ları", Collections.emptyList())

// System credentials store'u al
def store = SystemCredentialsProvider.getInstance().getStore()

// Domain'i ekle (yoksa oluşturulur)
def domainSpec = store.getDomainByName("APP_NAME")
if (domainSpec == null) {
    store.addDomain(domain)
    domainSpec = store.getDomainByName("APP_NAME")
}

// Credential'ları tanımla
def credentials = [
    [id: "TEST_APP_NAME_POSTGRES_USER", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_POSTGRES_PASSWORD", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_POSTGRES_DB", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_DATABASE_URL", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_NEXTAUTH_SECRET", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_NEXT_PUBLIC_APP_URL", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_AWS_ACCESS_KEY_ID", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_AWS_SECRET_ACCESS_KEY", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_AWS_REGION", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_BEDROCK_MODEL_ID", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_LDAP_HOST", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_LDAP_USERNAME", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_LDAP_PASSWORD", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_LDAP_PORT", description: "AÇIKLAMA", secret: "DEĞER"],
    [id: "TEST_APP_NAME_LDAP_BASE_DN", description: "AÇIKLAMA", secret: "DEĞER"]
]

// Her credential'ı ekle
credentials.each { cred ->
    try {
        // Mevcut credential'ı kontrol et
        def existing = store.getCredentials(domainSpec).find { it.id == cred.id }
        
        if (existing) {
            // Mevcut credential'ı sil
            store.removeCredentials(domainSpec, existing)
            println "✓ Mevcut credential silindi: ${cred.id}"
        }
        
        // Yeni credential oluştur
        def stringCredential = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            cred.id,
            cred.description,
            new hudson.util.Secret(cred.secret)
        )
        
        // Credential'ı ekle
        store.addCredentials(domainSpec, stringCredential)
        println "✓ Credential eklendi: ${cred.id} - ${cred.description}"
    } catch (Exception e) {
        println "✗ Hata (${cred.id}): ${e.message}"
    }
}

println "\n✅ Tüm credential'lar başarıyla yüklendi!"
