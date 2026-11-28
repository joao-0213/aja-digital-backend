package br.com.ajadigital.email.adapter.out.mail

import br.com.ajadigital.email.ports.out.DeliveryFailure
import br.com.ajadigital.email.ports.out.EmailSender
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
class HttpEmailSender(
    @Value("\${email.provider.url:}") private val endpoint: String,
    @Value("\${email.provider.token:}") private val providerToken: String,
    @Value("\${email.action-base-url:http://localhost:3000}") private val actionBaseUrl: String,
    private val mapper: ObjectMapper,
    @Value("\${email.provider.mode:http}") private val mode: String = "http",
    @Value("\${email.provider.from:}") private val from: String = "",
) : EmailSender {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private val provider = mode.lowercase()
    private val logoContent by lazy {
        javaClass.getResourceAsStream("/email/aja-symbol.png")?.use { java.util.Base64.getEncoder().encodeToString(it.readAllBytes()) }
            ?: error("email brand image is missing")
    }

    init {
        require(provider in setOf("http", "resend", "mailpit")) { "unsupported email provider mode" }
        if (provider != "http") require(from.isNotBlank()) { "email provider from is required" }
        if (provider == "resend") {
            require(providerToken.isNotBlank()) { "email provider token is required" }
            val uri = URI.create(endpoint.ifBlank { "https://api.resend.com/emails" })
            require(uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true) && uri.host == "127.0.0.1") {
                "Resend endpoint must use HTTPS"
            }
        }
    }

    override fun send(eventId: String, recipient: String, token: String, purpose: String) {
        val path = if (purpose == "email_confirmation") "/cadastro" else "/recuperar-senha"
        val actionUrl = "${actionBaseUrl.trimEnd('/')}$path?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"
        val confirming = purpose == "email_confirmation"
        val subject = if (confirming) "Confirme seu e-mail" else "Redefina sua senha"
        val action = if (confirming) "Confirmar e-mail" else "Redefinir senha"
        val intro = if (confirming) "Use o botão abaixo para confirmar seu endereço de e-mail." else "Use o botão abaixo para redefinir sua senha."
        val fallback = "$action:\n$actionUrl\n\nSe você não solicitou esta ação, ignore esta mensagem."
        val displayBody = "$intro Se você não solicitou esta ação, ignore esta mensagem."
        sendMessage(eventId, recipient, subject, actionUrl, fallback, action, actionUrl, displayBody)
    }

    override fun sendAnnouncement(eventId: String, recipient: String, title: String, body: String, announcementId: String) {
        val link = "${actionBaseUrl.trimEnd('/')}/avisos#aviso-${URLEncoder.encode(announcementId, StandardCharsets.UTF_8).replace("+", "%20")}"
        sendMessage(eventId, recipient, title, "$body\n\n$link", "$body\n\nLer aviso: $link", "Ler aviso", link, body)
    }

    private fun sendMessage(eventId: String, recipient: String, subject: String, text: String, fallback: String, actionLabel: String, actionUrl: String, displayBody: String) {
        val html = if (provider == "http") null else AjaEmailTemplate.render(subject, displayBody, actionLabel, actionUrl)
        val (target, payload) = when (provider) {
            "http" -> (endpoint.ifBlank { "http://127.0.0.1:9/email" }) to mapOf("to" to recipient, "subject" to subject, "text" to text)
            "resend" -> (endpoint.ifBlank { "https://api.resend.com/emails" }) to mapOf("from" to from, "to" to listOf(recipient), "subject" to subject, "text" to fallback, "html" to html, "attachments" to listOf(mapOf("filename" to "aja-symbol.png", "content" to logoContent, "content_type" to "image/png", "content_id" to "aja-symbol")))
            "mailpit" -> (endpoint.ifBlank { "http://localhost:8025/api/v1/send" }) to mapOf("From" to mapOf("Email" to from), "To" to listOf(mapOf("Email" to recipient)), "Subject" to subject, "Text" to fallback, "HTML" to html, "Attachments" to listOf(mapOf("Filename" to "aja-symbol.png", "Content" to logoContent, "ContentType" to "image/png", "ContentID" to "aja-symbol")))
            else -> error("unsupported email provider mode")
        }
        val body = mapper.writeValueAsBytes(payload)
        val request = HttpRequest.newBuilder(URI.create(target))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .apply { if (provider != "mailpit") header("Idempotency-Key", eventId) }
            .apply { if (provider != "mailpit" && providerToken.isNotBlank()) header("Authorization", "Bearer $providerToken") }
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.discarding())
        } catch (failure: java.io.IOException) {
            throw DeliveryFailure(ambiguous = true, failure)
        }
        val status = response.statusCode()
        if (status !in FIRST_SUCCESS_STATUS until FIRST_REDIRECTION_STATUS) {
            throw DeliveryFailure(ambiguous = status >= FIRST_SERVER_ERROR_STATUS)
        }
    }
}

private const val FIRST_SUCCESS_STATUS = 200
private const val FIRST_REDIRECTION_STATUS = 300
private const val FIRST_SERVER_ERROR_STATUS = 500
