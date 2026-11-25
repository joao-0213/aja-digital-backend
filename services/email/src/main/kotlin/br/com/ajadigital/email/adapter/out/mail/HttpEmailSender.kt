package br.com.ajadigital.email.adapter.out.mail

import br.com.ajadigital.email.ports.out.DeliveryFailure
import br.com.ajadigital.email.ports.out.EmailSender
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
        val subject = if (purpose == "email_confirmation") "Confirme seu e-mail" else "Redefina sua senha"
        sendMessage(eventId, recipient, subject, actionUrl)
    }

    override fun sendAnnouncement(eventId: String, recipient: String, title: String, body: String, announcementId: String) {
        val link = "${actionBaseUrl.trimEnd('/')}/avisos#aviso-${URLEncoder.encode(announcementId, StandardCharsets.UTF_8).replace("+", "%20")}"
        sendMessage(eventId, recipient, title, "$body\n\n$link")
    }

    private fun sendMessage(eventId: String, recipient: String, subject: String, text: String) {
        val (target, payload) = when (provider) {
            "http" -> (endpoint.ifBlank { "http://127.0.0.1:9/email" }) to mapOf("to" to recipient, "subject" to subject, "text" to text)
            "resend" -> (endpoint.ifBlank { "https://api.resend.com/emails" }) to mapOf("from" to from, "to" to listOf(recipient), "subject" to subject, "text" to text)
            "mailpit" -> (endpoint.ifBlank { "http://localhost:8025/api/v1/send" }) to mapOf("From" to mapOf("Email" to from), "To" to listOf(mapOf("Email" to recipient)), "Subject" to subject, "Text" to text)
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
        val response = try { client.send(request, HttpResponse.BodyHandlers.discarding()) }
        catch (failure: HttpTimeoutException) { throw DeliveryFailure(ambiguous = true, failure) }
        catch (failure: java.io.IOException) { throw DeliveryFailure(ambiguous = true, failure) }
        if (response.statusCode() !in 200..299) throw DeliveryFailure(ambiguous = response.statusCode() >= 500)
    }
}
