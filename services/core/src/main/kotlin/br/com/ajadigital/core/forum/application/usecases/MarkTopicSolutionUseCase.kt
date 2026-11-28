package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.GetStoredTopicQuery
import br.com.ajadigital.core.forum.application.models.MarkTopicSolutionCommand
import br.com.ajadigital.core.forum.application.models.MarkTopicSolutionRecord
import br.com.ajadigital.core.forum.application.ports.`in`.MarkTopicSolutionInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.domain.exceptions.ForumConflict
import br.com.ajadigital.core.forum.domain.exceptions.ForumNotFound
import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class MarkTopicSolutionUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : MarkTopicSolutionInputPort {
    override fun solution(request: MarkTopicSolutionCommand): ForumTopicView = transactionRunner.run {
        val actor = request.actor
        val id = request.id
        val revision = request.revision
        val replyId = request.replyId
        ForumPolicy.writable(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(revision > 0 && (replyId == null || ForumPolicy.validId(replyId)))
        val topicId = ForumPolicy.checkedId(id)
        store.solution(MarkTopicSolutionRecord(actor.id, actor.role, actor.sessionValidationReference, topicId, revision, replyId))
            ?: store.topic(GetStoredTopicQuery(actor.id, actor.role, actor.sessionValidationReference, topicId))?.let { throw ForumConflict(it.revision) }
            ?: throw ForumNotFound()
    }
}
