package br.com.ajadigital.core.assessment.adapters.out.questions

import br.com.ajadigital.core.assessment.domain.exceptions.QuestionsUnavailable
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HttpQuestionSnapshotProviderTests {
    @Test
    fun `snapshot request times out when Questions stalls`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        server.createContext("/snapshots") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            try {
                Thread.sleep(10_000)
                exchange.responseBody.use { it.write("""{"snapshots":[]}""".toByteArray()) }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        server.start()
        try {
            val provider = HttpQuestionSnapshotProvider("http://127.0.0.1:${server.address.port}/snapshots", "a-test-token-that-is-at-least-32-bytes")
            val start = System.nanoTime()
            assertThrows(QuestionsUnavailable::class.java) { provider.snapshots(br.com.ajadigital.core.assessment.domain.models.QuestionSnapshotProviderSnapshotsDto(listOf("q1"))) }
            val elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start)
            assertTrue(elapsed < 5, "request should honor its bounded read timeout, took ${elapsed}s")
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
