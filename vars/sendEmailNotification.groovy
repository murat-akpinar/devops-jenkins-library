def resolveRecipients(String input) {
    if (!input?.trim()) return ''
    def teams = emailTeams()
    return input.split(',').collect { token ->
        def key = token.trim()
        teams.containsKey(key) ? teams[key] : key
    }.join(', ')
}

def call(Map config) {
    def status = config.status // 'success' veya 'failure'
    def appName = config.appName
    def recipientEmail = resolveRecipients(config.recipientEmail)
    def fromEmail = config.fromEmail ?: 'devops@your-company.com'
    
    // Pipeline ismini al (Jenkins'te oluşturulan job ismi)
    // env.JOB_NAME Jenkins pipeline context'inde otomatik olarak mevcuttur
    def pipelineName = appName
    try {
        def jobName = env.JOB_NAME
        if (jobName) {
            pipelineName = jobName.toString()
        }
    } catch (Exception ignored) {
        // env nesnesi yoksa veya JOB_NAME yoksa appName kullanılır
        pipelineName = appName
    }
    
    // Pipeline isminden ortam bilgisini çıkar
    // Öncelik sırası: preprod önce kontrol edilmeli, aksi halde 'prod' içerdiği için PROD algılanır
    def environment = 'TEST'  // varsayılan
    if (pipelineName.toLowerCase().contains('preprod')) {
        environment = 'PREPROD'
    } else if (pipelineName.toLowerCase().contains('prod')) {
        environment = 'PROD'
    } else if (pipelineName.toLowerCase().contains('test')) {
        environment = 'TEST'
    }

    // Pipeline isminden proje adını çıkar (sonunda ortam bilgisi varsa temizle)
    // Groovy'de case-insensitive için (?i) modifier kullanılır
    def projectName = pipelineName
        .replaceAll(/(?i)-?(preprod|prod|test)$/, '')
        .replaceAll(/(?i)-preprod-|-prod-|-test-/, '-')
        .trim()
    
    // Eğer pipeline isminden proje adı çıkarılamadıysa appName kullan
    if (!projectName || projectName == pipelineName || projectName.isEmpty()) {
        projectName = appName
    }
    
    def isSuccess  = status == 'success'
    def isAborted  = status == 'aborted'

    def title = isSuccess ?
        "${projectName} - ${environment} Deployu Başarıyla Tamamlandı" :
        (isAborted ? "${projectName} - ${environment} Deployu İptal Edildi" : "${projectName} - ${environment} Deployu Başarısız Oldu")

    def subject = isSuccess ?
        "Jenkins - ${projectName} [${environment}] Deployu Başarılı" :
        (isAborted ? "Jenkins - ${projectName} [${environment}] Deployu İptal Edildi" : "Jenkins - ${projectName} [${environment}] Deployu Başarısız")

    def headerColor  = isSuccess ? '#28a745' : (isAborted ? '#6c757d' : '#dc3545')
    def statusLabel  = isSuccess ? 'BAŞARILI' : (isAborted ? 'İPTAL' : 'BAŞARISIZ')
    def buildNumber  = env?.BUILD_NUMBER  ?: '-'
    def buildUrl     = env?.BUILD_URL     ?: '#'
    def nowStr       = new Date().format('dd.MM.yyyy HH:mm:ss', TimeZone.getTimeZone('Europe/Istanbul'))

    def body = """<!DOCTYPE html>
<html lang="tr">
<head>
  <meta charset="UTF-8">
  <title>${title}</title>
</head>
<body style="margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f4;padding:30px 0;">
    <tr>
      <td align="center">
        <table width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:6px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.12);">

          <!-- HEADER -->
          <tr>
            <td align="center" style="background-color:${headerColor};padding:28px 20px;">
              <h1 style="margin:0;color:#ffffff;font-size:20px;font-weight:bold;letter-spacing:0.5px;">${title}</h1>
            </td>
          </tr>

          <!-- DETAIL TABLE -->
          <tr>
            <td style="padding:28px 32px 20px;">
              <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;font-size:14px;">

                <tr style="border-bottom:1px solid #eeeeee;">
                  <td style="padding:11px 8px;color:#888888;width:38%;font-weight:bold;">Proje</td>
                  <td style="padding:11px 8px;color:#222222;">${projectName}</td>
                </tr>

                <tr style="border-bottom:1px solid #eeeeee;">
                  <td style="padding:11px 8px;color:#888888;font-weight:bold;">Ortam</td>
                  <td style="padding:11px 8px;">
                    <span style="background-color:${headerColor};color:#ffffff;padding:3px 10px;border-radius:3px;font-size:12px;font-weight:bold;">${environment}</span>
                  </td>
                </tr>

                <tr style="border-bottom:1px solid #eeeeee;">
                  <td style="padding:11px 8px;color:#888888;font-weight:bold;">Durum</td>
                  <td style="padding:11px 8px;">
                    <span style="background-color:${headerColor};color:#ffffff;padding:3px 10px;border-radius:3px;font-size:12px;font-weight:bold;">${statusLabel}</span>
                  </td>
                </tr>

                <tr style="border-bottom:1px solid #eeeeee;">
                  <td style="padding:11px 8px;color:#888888;font-weight:bold;">Pipeline</td>
                  <td style="padding:11px 8px;color:#222222;">${pipelineName}</td>
                </tr>

                <tr style="border-bottom:1px solid #eeeeee;">
                  <td style="padding:11px 8px;color:#888888;font-weight:bold;">Build No</td>
                  <td style="padding:11px 8px;color:#1a73e8;font-weight:bold;">
                    <a href="${buildUrl}" style="color:#1a73e8;text-decoration:none;">#${buildNumber}</a>
                  </td>
                </tr>

                <tr style="border-bottom:1px solid #eeeeee;">
                  <td style="padding:11px 8px;color:#888888;font-weight:bold;">Tarih / Saat</td>
                  <td style="padding:11px 8px;color:#1a73e8;">${nowStr}</td>
                </tr>

                ${isSuccess ? '' : """
                <tr>
                  <td style="padding:11px 8px;color:#888888;font-weight:bold;">Jenkins Log</td>
                  <td style="padding:11px 8px;">
                    <a href="${buildUrl}console" style="color:#dc3545;text-decoration:none;font-weight:bold;">Hata Logunu İncele &rarr;</a>
                  </td>
                </tr>"""}

              </table>

              ${isSuccess ? '' : """
              <table width="100%" cellpadding="0" cellspacing="0" style="margin-top:18px;">
                <tr>
                  <td style="background-color:#fff3cd;border-left:4px solid #ffc107;padding:12px 16px;border-radius:0 4px 4px 0;font-size:13px;color:#856404;">
                    Lütfen Jenkins loglarını inceleyerek hatanın nedenini tespit ediniz ve sorunu çözdükten sonra deploy işlemini tekrar başlatınız.
                  </td>
                </tr>
              </table>"""}

            </td>
          </tr>

          <!-- FOOTER -->
          <tr>
            <td align="center" style="background-color:#f8f9fa;border-top:1px solid #eeeeee;padding:14px 20px;">
              <p style="margin:0;font-size:12px;color:#aaaaaa;">
                Bu e-posta Jenkins CI/CD sistemi tarafından otomatik olarak gönderilmiştir.<br>
                <a href="mailto:${fromEmail}" style="color:#aaaaaa;text-decoration:none;">${fromEmail}</a>
              </p>
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>
</body>
</html>"""
    
    emailext(
        attachLog: true,
        body: body,
        subject: subject,
        to: recipientEmail,
        mimeType: 'text/html',
        from: fromEmail
    )
}

