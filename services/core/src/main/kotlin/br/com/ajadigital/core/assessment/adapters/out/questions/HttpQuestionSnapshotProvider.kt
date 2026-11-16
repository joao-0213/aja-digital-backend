package br.com.ajadigital.core.assessment.adapters.out.questions

import br.com.ajadigital.core.assessment.domain.exceptions.QuestionSnapshotNotFound
import br.com.ajadigital.core.assessment.domain.exceptions.QuestionsUnavailable
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.nio.charset.StandardCharsets
import java.time.Duration


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
