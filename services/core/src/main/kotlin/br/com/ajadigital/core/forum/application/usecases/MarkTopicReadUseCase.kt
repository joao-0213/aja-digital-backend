package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.MarkTopicReadInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.MarkTopicReadCommand
import br.com.ajadigital.core.forum.application.models.MarkTopicReadRecord
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class MarkTopicReadUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : MarkTopicReadInputPort {
    override fun read(request: MarkTopicReadCommand): ForumTopicView = transactionRunner.run(TransactionRunDto {
        val (actor, id) = request
                ForumPolicy.readable(actor).let { store.read(MarkTopicReadRecord(actor.id, actor.role, actor.sessionValidationReference, ForumPolicy.checkedId(id))) ?: throw AcademicNotFound() }
    })
}
