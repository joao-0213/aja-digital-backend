package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy

import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.WithdrawTopicReplyInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.WithdrawTopicReplyCommand
import br.com.ajadigital.core.forum.application.models.WithdrawTopicReplyRecord
import br.com.ajadigital.core.forum.application.models.GetStoredTopicQuery
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class WithdrawTopicReplyUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : WithdrawTopicReplyInputPort {
    override fun withdrawReply(request: WithdrawTopicReplyCommand): ForumTopicView = transactionRunner.run {
        val (actor, id, replyId, revision) = request
                ForumPolicy.writable(actor)
        require(revision > 0)
        val topicId = ForumPolicy.checkedId(id)
        val checkedReplyId = ForumPolicy.checkedId(replyId)
        store.withdrawReply(WithdrawTopicReplyRecord(actor.id, actor.role, actor.sessionValidationReference, topicId, checkedReplyId, revision))
            ?: store.topic(GetStoredTopicQuery(actor.id, actor.role, actor.sessionValidationReference, topicId))?.let { topic ->
                if (topic.replies.any { it.id == checkedReplyId }) throw AcademicConflict()
                throw AcademicNotFound()
            } ?: throw AcademicNotFound()
    }
}
