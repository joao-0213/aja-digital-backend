package br.com.ajadigital.core.forum.adapters.out.questions

import br.com.ajadigital.core.forum.application.models.ForumQuestionSnapshot
import br.com.ajadigital.core.forum.application.models.ForumQuestionSnapshotQuery
import br.com.ajadigital.core.forum.application.ports.out.ForumQuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.forum.domain.exceptions.ForumQuestionSnapshotNotFound
import br.com.ajadigital.core.forum.domain.exceptions.ForumQuestionsUnavailable
import br.com.ajadigital.core.shared.adapters.out.questions.QuestionsSnapshotHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException

@Component
class HttpForumQuestionSnapshotProvider(
    @Value("\${core.questions.snapshots-url}") url: String,
    @Value("\${core.internal.questions-token}") token: String,
) : ForumQuestionSnapshotProviderOutputPort {
    private val client = QuestionsSnapshotHttpClient(url, token)

    override fun snapshots(request: ForumQuestionSnapshotQuery): List<ForumQuestionSnapshot> = try {
        client.fetch(request.ids).map { ForumQuestionSnapshot(it.questionId) }
    } catch (error: RestClientResponseException) {
        when (error.statusCode.value()) {
            404 -> throw ForumQuestionSnapshotNotFound()
            422 -> throw IllegalArgumentException()
            else -> throw ForumQuestionsUnavailable()
        }
    } catch (error: Exception) { throw ForumQuestionsUnavailable().also { it.initCause(error) } }
}
