package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy

import br.com.ajadigital.core.forum.domain.exceptions.ForumNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.VoteTopicReplyInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.VoteTopicReplyCommand
import br.com.ajadigital.core.forum.application.models.VoteTopicReplyRecord
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class VoteTopicReplyUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : VoteTopicReplyInputPort {
    override fun vote(request: VoteTopicReplyCommand): ForumTopicView = transactionRunner.run {
        val (actor, id, replyId, voted) = request
                ForumPolicy.writable(actor.role, actor.active, actor.emailConfirmedAt != null)
        store.vote(VoteTopicReplyRecord(actor.id, actor.role, actor.sessionValidationReference, ForumPolicy.checkedId(id), ForumPolicy.checkedId(replyId), voted)) ?: throw ForumNotFound()
    }
}
