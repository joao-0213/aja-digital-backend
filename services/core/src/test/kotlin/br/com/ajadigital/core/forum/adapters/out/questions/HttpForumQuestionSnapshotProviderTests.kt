package br.com.ajadigital.core.forum.adapters.out.questions

import br.com.ajadigital.core.forum.application.models.ForumQuestionSnapshotQuery
import br.com.ajadigital.core.forum.domain.exceptions.ForumQuestionSnapshotNotFound
import br.com.ajadigital.core.forum.domain.exceptions.ForumQuestionsUnavailable
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class HttpForumQuestionSnapshotProviderTests {
    private val token = "test-service-token-that-is-at-least-32-bytes"

    @Test
    fun `snapshot adapter sends the private request and returns only validation facts`() {
        var authorization: String? = null
        var body: String? = null
        val server = server(200, snapshot) { exchange ->
            authorization = exchange.requestHeaders.getFirst("X-AJA-Service-Token")
            body = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
        }
        try {
            val result = HttpForumQuestionSnapshotProvider(url(server), token)
                .snapshots(ForumQuestionSnapshotQuery(listOf("q-1")))
            assertEquals(token, authorization)
            assertEquals("""{"ids":["q-1"]}""", body)
            assertEquals("q-1", result.single().questionId)
        } finally { server.stop(0) }
    }

    @Test
    fun `snapshot adapter preserves missing invalid unavailable and malformed response behavior`() {
        assertThrows(ForumQuestionSnapshotNotFound::class.java) { withServer(404, "") }
        assertThrows(IllegalArgumentException::class.java) { withServer(422, "") }
        assertThrows(ForumQuestionsUnavailable::class.java) { withServer(503, "") }
        assertThrows(ForumQuestionsUnavailable::class.java) { withServer(200, """{"snapshots":[{"questionId":"q-1"}]}""") }
    }

    private fun withServer(status: Int, response: String) {
        val server = server(status, response)
        try { HttpForumQuestionSnapshotProvider(url(server), token).snapshots(ForumQuestionSnapshotQuery(listOf("q-1"))) }
        finally { server.stop(0) }
    }

    private fun server(status: Int, response: String, action: (com.sun.net.httpserver.HttpExchange) -> Unit = {}) =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/snapshots") { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/json")
                action(exchange)
                val bytes = response.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

    private fun url(server: HttpServer) = "http://127.0.0.1:${server.address.port}/snapshots"

    private val snapshot = """{"snapshots":[{"questionId":"q-1","revision":1,"contentHash":"${"a".repeat(64)}","subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"Q?","options":["A","B"],"correctOption":0,"explanation":"","image":null}]}"""
}
