package br.com.ajadigital.email

import br.com.ajadigital.email.adapter.out.mail.HttpEmailSender
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class HttpEmailSenderTests {
    @Test
    fun resendRejectsNonHttpsNonLoopbackEndpointBeforeSending() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpEmailSender("http://localhost:8025/api/v1/send", "secret", "http://localhost:3000", JsonMapper.builder().build(), "resend", "noreply@example.test")
        }
    }

    @Test
    fun announcementTemplateSendsTitleBodyAndStableProviderKey() {
        val receivedBody = AtomicReference<String>()
        val receivedKey = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/email") { exchange ->
            receivedBody.set(exchange.requestBody.readAllBytes().decodeToString())
            receivedKey.set(exchange.requestHeaders.getFirst("Idempotency-Key"))
            exchange.sendResponseHeaders(202, -1)
            exchange.close()
        }
        server.start()
        try {
            HttpEmailSender("http://127.0.0.1:${server.address.port}/email", "", "http://localhost:3000", JsonMapper.builder().build())
                .sendAnnouncement("event-1", "student@example.test", "Class update", "Tomorrow at 10:30", "announcement-1")

            val body = JsonMapper.builder().build().readTree(receivedBody.get())
            assertEquals("student@example.test", body.get("to").asString())
            assertEquals("Class update", body.get("subject").asString())
            assertTrue(body.get("text").asString().contains("Tomorrow at 10:30\n\nhttp://localhost:3000/avisos#aviso-announcement-1"))
            assertEquals("event-1", receivedKey.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun resendModeUsesProviderPayloadAndFrontendActionLinks() {
        val payload = AtomicReference<String>()
        val headers = AtomicReference<com.sun.net.httpserver.Headers>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/emails") { exchange ->
            payload.set(exchange.requestBody.readAllBytes().decodeToString())
            headers.set(exchange.requestHeaders)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            HttpEmailSender("http://127.0.0.1:${server.address.port}/emails", "secret", "http://localhost:3000/", JsonMapper.builder().build(), "resend", "AJA <noreply@example.test>")
                .send("event-2", "student@example.test", "a+b/c", "email_confirmation")
            val body = JsonMapper.builder().build().readTree(payload.get())
            assertEquals("AJA <noreply@example.test>", body.get("from").asString())
            assertEquals("student@example.test", body.get("to")[0].asString())
            assertEquals("Confirme seu e-mail", body.get("subject").asString())
            assertTrue(body.get("text").asString().contains("http://localhost:3000/cadastro?token=a%2Bb%2Fc"))
            assertTrue(body.get("text").asString().contains("Se você não solicitou"))
            assertTrue(body.get("text").asString().contains("http://localhost:3000/cadastro?token=a%2Bb%2Fc"))
            assertTrue(body.get("html").asString().contains("Confirmar e-mail"))
            assertTrue(body.get("html").asString().contains("Use o botão abaixo para confirmar seu endereço de e-mail."))
            assertTrue(body.get("html").asString().contains("http://localhost:3000/cadastro?token=a%2Bb%2Fc"))
            assertTrue(body.get("html").asString().contains("cid:aja-symbol"))
            assertEquals("aja-symbol.png", body.get("attachments")[0].get("filename").asString())
            assertEquals("aja-symbol", body.get("attachments")[0].get("content_id").asString())
            assertEquals("image/png", body.get("attachments")[0].get("content_type").asString())
            assertEquals(java.util.Base64.getEncoder().encodeToString(javaClass.getResourceAsStream("/email/aja-symbol.png")!!.readAllBytes()), body.get("attachments")[0].get("content").asString())
            assertEquals("Bearer secret", headers.get().getFirst("Authorization"))
            assertEquals("event-2", headers.get().getFirst("Idempotency-Key"))
        } finally { server.stop(0) }
    }

    @Test
    fun mailpitModeUsesItsSendApiShape() {
        val payload = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/send") { exchange ->
            payload.set(exchange.requestBody.readAllBytes().decodeToString())
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            HttpEmailSender("http://127.0.0.1:${server.address.port}/api/v1/send", "", "http://localhost:3000", JsonMapper.builder().build(), "mailpit", "noreply@example.test")
                .send("event-3", "student@example.test", "token", "password_reset")
            val body = JsonMapper.builder().build().readTree(payload.get())
            assertEquals("noreply@example.test", body.get("From").get("Email").asString())
            assertEquals("student@example.test", body.get("To")[0].get("Email").asString())
            assertEquals("Redefina sua senha", body.get("Subject").asString())
            assertTrue(body.get("Text").asString().contains("/recuperar-senha?token=token"))
            assertTrue(body.get("Text").asString().contains("Se você não solicitou"))
            assertTrue(body.get("HTML").asString().contains("Redefinir senha"))
            assertTrue(body.get("HTML").asString().contains("Use o botão abaixo para redefinir sua senha."))
            assertTrue(body.get("HTML").asString().contains("cid:aja-symbol"))
            assertEquals("aja-symbol", body.get("Attachments")[0].get("ContentID").asString())
            assertEquals(java.util.Base64.getEncoder().encodeToString(javaClass.getResourceAsStream("/email/aja-symbol.png")!!.readAllBytes()), body.get("Attachments")[0].get("Content").asString())
        } finally { server.stop(0) }
    }

    @Test
    fun announcementIncludesItsEncodedFrontendFragment() {
        val payload = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/email") { exchange ->
            payload.set(exchange.requestBody.readAllBytes().decodeToString())
            exchange.sendResponseHeaders(202, -1)
            exchange.close()
        }
        server.start()
        try {
            HttpEmailSender("http://127.0.0.1:${server.address.port}/email", "", "http://localhost:3000/", JsonMapper.builder().build())
                .sendAnnouncement("event-4", "student@example.test", "Class update", "Tomorrow", "id with/slash")
            val text = JsonMapper.builder().build().readTree(payload.get()).get("text").asString()
            assertTrue(text.contains("http://localhost:3000/avisos#aviso-id%20with%2Fslash"))
        } finally { server.stop(0) }
    }

    @Test
    fun announcementHtmlEscapesUserContentAndKeepsPlainTextFallback() {
        val payload = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/emails") { exchange ->
            payload.set(exchange.requestBody.readAllBytes().decodeToString())
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            HttpEmailSender("http://127.0.0.1:${server.address.port}/emails", "secret", "https://app.example.test/", JsonMapper.builder().build(), "resend", "AJA <noreply@example.test>")
                .sendAnnouncement("event-xss", "student@example.test", "<img src=x onerror=alert(1)>", "Hello <script>alert(1)</script> & goodbye's", "id-1")
            val body = JsonMapper.builder().build().readTree(payload.get())
            val html = body.get("html").asString()
            assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"))
            assertTrue(html.contains("Hello &lt;script&gt;alert(1)&lt;/script&gt; &amp; goodbye&#39;s"))
            assertTrue(!html.contains("<script>"))
            assertTrue(body.get("text").asString().contains("Hello <script>alert(1)</script> & goodbye's"))
            assertTrue(body.get("html").asString().contains("https://app.example.test/avisos#aviso-id-1"))
            assertTrue(body.get("html").asString().contains("Ler aviso"))
        } finally { server.stop(0) }
    }
}
