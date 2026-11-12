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
    @Value("\${email.provider.url:http://127.0.0.1:9/email}") private val endpoint: String,
    @Value("\${email.provider.token:}") private val providerToken: String,
    @Value("\${email.action-base-url:http://localhost:3000}") private val actionBaseUrl: String,
    private val mapper: ObjectMapper,
) : EmailSender {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    override fun send(eventId: String, recipient: String, token: String, purpose: String) {
        val path = if (purpose == "email_confirmation") "/confirm-email" else "/reset-password"
        val actionUrl = "$actionBaseUrl$path?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"
        val subject = if (purpose == "email_confirmation") "Confirme seu e-mail" else "Redefina sua senha"
        sendMessage(eventId, recipient, subject, actionUrl)
    }

    override fun sendAnnouncement(eventId: String, recipient: String, title: String, body: String) {
        sendMessage(eventId, recipient, title, body)
    }

    private fun sendMessage(eventId: String, recipient: String, subject: String, text: String) {
        val body = mapper.writeValueAsBytes(mapOf("to" to recipient, "subject" to subject, "text" to text))
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", eventId)
            .apply { if (providerToken.isNotBlank()) header("Authorization", "Bearer $providerToken") }
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        val response = try { client.send(request, HttpResponse.BodyHandlers.discarding()) }
        catch (failure: HttpTimeoutException) { throw DeliveryFailure(ambiguous = true, failure) }
        catch (failure: java.io.IOException) { throw DeliveryFailure(ambiguous = true, failure) }
        if (response.statusCode() !in 200..299) throw DeliveryFailure(ambiguous = response.statusCode() >= 500)
    }
}
