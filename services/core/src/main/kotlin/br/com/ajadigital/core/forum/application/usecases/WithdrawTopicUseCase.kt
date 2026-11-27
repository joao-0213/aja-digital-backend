package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.WithdrawTopicInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.WithdrawTopicCommand
import br.com.ajadigital.core.forum.application.models.WithdrawTopicRecord
import br.com.ajadigital.core.forum.application.models.GetStoredTopicQuery
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class WithdrawTopicUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : WithdrawTopicInputPort {
    override fun withdraw(request: WithdrawTopicCommand): ForumTopicView = transactionRunner.run(TransactionRunDto {
        val (actor, id, revision) = request
                ForumPolicy.writable(actor)
        require(revision > 0)
        val topicId = ForumPolicy.checkedId(id)
        store.withdraw(WithdrawTopicRecord(actor.id, actor.role, actor.sessionValidationReference, topicId, revision))
            ?: store.topic(GetStoredTopicQuery(actor.id, actor.role, actor.sessionValidationReference, topicId))?.let { throw AcademicConflict(it.revision) }
            ?: throw AcademicNotFound()
    })
}
