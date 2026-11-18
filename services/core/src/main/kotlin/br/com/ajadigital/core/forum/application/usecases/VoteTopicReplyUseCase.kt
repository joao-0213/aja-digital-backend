package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.VoteTopicReplyInputPort
import br.com.ajadigital.core.forum.domain.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.VoteTopicReplyDto
import br.com.ajadigital.core.forum.domain.models.VoteTopicReplyRecordDto
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class VoteTopicReplyUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : VoteTopicReplyInputPort {
    override fun vote(request: VoteTopicReplyDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, id, replyId, voted) = request
                ForumPolicy.writable(actor)
        store.vote(VoteTopicReplyRecordDto(actor.id, actor.role, actor.sessionValidationReference, ForumPolicy.checkedId(id), ForumPolicy.checkedId(replyId), voted)) ?: throw AcademicNotFound()
    })
}
