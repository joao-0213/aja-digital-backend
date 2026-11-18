package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.WithdrawTopicReplyInputPort
import br.com.ajadigital.core.forum.domain.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.WithdrawTopicReplyDto
import br.com.ajadigital.core.forum.domain.models.WithdrawTopicReplyRecordDto
import br.com.ajadigital.core.forum.domain.models.GetStoredTopicDto
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class WithdrawTopicReplyUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : WithdrawTopicReplyInputPort {
    override fun withdrawReply(request: WithdrawTopicReplyDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, id, replyId, revision) = request
                ForumPolicy.writable(actor)
        require(revision > 0)
        val topicId = ForumPolicy.checkedId(id)
        val checkedReplyId = ForumPolicy.checkedId(replyId)
        store.withdrawReply(WithdrawTopicReplyRecordDto(actor.id, actor.role, actor.sessionValidationReference, topicId, checkedReplyId, revision))
            ?: store.topic(GetStoredTopicDto(actor.id, actor.role, actor.sessionValidationReference, topicId))?.let { topic ->
                if ((topic["replies"] as? List<*>)?.any { (it as? Map<*, *>)?.get("id") == checkedReplyId } == true) throw AcademicConflict()
                throw AcademicNotFound()
            } ?: throw AcademicNotFound()
    })
}
