package br.com.ajadigital.core.assessment.adapters.out.questions

import br.com.ajadigital.core.assessment.application.models.QuestionSnapshotQuery

import br.com.ajadigital.core.assessment.domain.exceptions.QuestionSnapshotNotFound
import br.com.ajadigital.core.assessment.domain.exceptions.QuestionsUnavailable
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot
import br.com.ajadigital.core.assessment.application.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.shared.adapters.out.questions.QuestionsSnapshotHttpClient
import br.com.ajadigital.core.shared.adapters.out.questions.QuestionSnapshotWire
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException


@Component
class HttpQuestionSnapshotProvider(
    @Value("\${core.questions.snapshots-url}") url: String,
    @Value("\${core.internal.questions-token}") token: String,
) : QuestionSnapshotProviderOutputPort {
    private val client = QuestionsSnapshotHttpClient(url, token)

    override fun snapshots(request: QuestionSnapshotQuery): List<BankQuestionSnapshot> = try {
        client.fetch(request.ids).map(QuestionSnapshotWire::toSnapshot)
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

private fun QuestionSnapshotWire.toSnapshot() = BankQuestionSnapshot(
    questionId, revision, contentHash, subject, topic, difficulty, institution, year, statement, options,
    correctOption, explanation, image?.let { br.com.ajadigital.core.assessment.domain.models.BankQuestionImage(it.fileId, it.alt) },
)
