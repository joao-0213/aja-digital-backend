package br.com.ajadigital.core.adapter.out.questions

import br.com.ajadigital.core.application.academic.QuestionSnapshotNotFound
import br.com.ajadigital.core.application.academic.QuestionsUnavailable
import br.com.ajadigital.core.ports.out.BankQuestionImage
import br.com.ajadigital.core.ports.out.BankQuestionSnapshot
import br.com.ajadigital.core.ports.out.QuestionSnapshotProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.nio.charset.StandardCharsets
import java.time.Duration

private data class SnapshotRequest(val ids: List<String>)
private data class SnapshotResponse(val snapshots: List<SnapshotDto>)
private data class SnapshotDto(
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
    val image: ImageDto?,
) {
    fun toSnapshot() = BankQuestionSnapshot(questionId, revision, contentHash, subject, topic, difficulty, institution, year, statement, options, correctOption, explanation, image?.let { BankQuestionImage(it.fileId, it.alt) })
}
private data class ImageDto(val fileId: String, val alt: String)

@Component
class HttpQuestionSnapshotProvider(
    @Value("\${core.questions.snapshots-url}") url: String,
    @Value("\${core.internal.questions-token}") token: String,
) : QuestionSnapshotProvider {
    private val client = RestClient.builder().baseUrl(url).requestFactory(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(2))
            setReadTimeout(Duration.ofSeconds(2))
        },
    ).build()
    private val expectedToken = token.toByteArray(StandardCharsets.UTF_8)

    init { require(expectedToken.size >= 32) }

    override fun snapshots(ids: List<String>): List<BankQuestionSnapshot> = try {
        val response = client.post().uri("")
            .header("X-AJA-Service-Token", String(expectedToken, StandardCharsets.UTF_8))
            .body(SnapshotRequest(ids))
            .retrieve().body(SnapshotResponse::class.java) ?: throw QuestionsUnavailable()
        response.snapshots.map(SnapshotDto::toSnapshot)
    } catch (error: RestClientResponseException) {
        when (error.statusCode.value()) {
            404 -> throw QuestionSnapshotNotFound()
            422 -> throw IllegalArgumentException()
            else -> throw QuestionsUnavailable()
        }
    } catch (_: Exception) {
        throw QuestionsUnavailable()
    }
}
