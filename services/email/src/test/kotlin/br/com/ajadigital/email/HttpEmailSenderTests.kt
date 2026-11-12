package br.com.ajadigital.email

import br.com.ajadigital.email.adapter.out.mail.HttpEmailSender
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class HttpEmailSenderTests {
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
                .sendAnnouncement("event-1", "student@example.test", "Class update", "Tomorrow at 10:30")

            val body = JsonMapper.builder().build().readTree(receivedBody.get())
            assertEquals("student@example.test", body.get("to").asString())
            assertEquals("Class update", body.get("subject").asString())
            assertEquals("Tomorrow at 10:30", body.get("text").asString())
            assertEquals("event-1", receivedKey.get())
        } finally {
            server.stop(0)
        }
    }
}
