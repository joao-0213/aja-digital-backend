package br.com.ajadigital.core.shared.adapters.out.questions

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.time.Duration

internal class QuestionsSnapshotHttpClient(
    url: String,
    token: String,
) {
    private val client =
        RestClient
            .builder()
            .baseUrl(url)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(2))
                    setReadTimeout(Duration.ofSeconds(2))
                },
            ).build()
    private val expectedToken = token.toByteArray(StandardCharsets.UTF_8)

    init {
        require(expectedToken.size >= MIN_SERVICE_TOKEN_BYTES)
    }

    fun fetch(ids: List<String>): List<QuestionSnapshotWire> = client
        .post()
        .uri("")
        .header("X-AJA-Service-Token", String(expectedToken, StandardCharsets.UTF_8))
        .body(QuestionSnapshotRequest(ids))
        .retrieve()
        .body(QuestionSnapshotResponse::class.java)
        ?.snapshots ?: throw IllegalStateException("Question snapshot response is empty")
}

internal data class QuestionSnapshotRequest(
    val ids: List<String>,
)

internal data class QuestionSnapshotResponse(
    val snapshots: List<QuestionSnapshotWire>,
)

internal data class QuestionSnapshotWire(
    val questionId: String,
    val revision: Int,
    val contentHash: String,
    val subject: String,
    val topic: String,
    val difficulty: String,
    val institution: String,
    val year: Int,
    val statement: String,
    val options: List<String>,
    val correctOption: Int,
    val explanation: String,
    val image: QuestionSnapshotImageWire?,
)

internal data class QuestionSnapshotImageWire(
    val fileId: String,
    val alt: String,
)

private const val MIN_SERVICE_TOKEN_BYTES = 32
