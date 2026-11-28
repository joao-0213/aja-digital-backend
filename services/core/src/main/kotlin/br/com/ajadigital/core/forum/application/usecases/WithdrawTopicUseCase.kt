package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy

import br.com.ajadigital.core.forum.domain.exceptions.ForumConflict
import br.com.ajadigital.core.forum.domain.exceptions.ForumNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.WithdrawTopicInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.WithdrawTopicCommand
import br.com.ajadigital.core.forum.application.models.WithdrawTopicRecord
import br.com.ajadigital.core.forum.application.models.GetStoredTopicQuery
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class WithdrawTopicUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : WithdrawTopicInputPort {
    override fun withdraw(request: WithdrawTopicCommand): ForumTopicView = transactionRunner.run {
        val (actor, id, revision) = request
                ForumPolicy.writable(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(revision > 0)
        val topicId = ForumPolicy.checkedId(id)
        store.withdraw(WithdrawTopicRecord(actor.id, actor.role, actor.sessionValidationReference, topicId, revision))
            ?: store.topic(GetStoredTopicQuery(actor.id, actor.role, actor.sessionValidationReference, topicId))?.let { throw ForumConflict(it.revision) }
            ?: throw ForumNotFound()
    }
}
