package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.SubmissionCreateRecord

import br.com.ajadigital.core.assessment.application.models.CreateSubmissionCommand

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.CreateSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class CreateSubmissionUseCase(private val store: SubmissionStoreOutputPort) : CreateSubmissionInputPort {
    override fun create(request: CreateSubmissionCommand): br.com.ajadigital.core.assessment.domain.models.SubmissionView {
                val (actor, key, body) = request
        SubmissionPolicy.student(actor.role, actor.active, actor.emailConfirmedAt != null)
        SubmissionPolicy.validKey(key)
        require(body.fieldsValid)
        val assessmentId = (body.assessmentId ?: throw IllegalArgumentException()).also(SubmissionPolicy::id)
        val attachments = SubmissionPolicy.attachments(SubmissionContracts.attachments(body.attachments ?: throw IllegalArgumentException()))
        return store.create(SubmissionCreateRecord(actor, assessmentId, attachments, key, request.requestHash))
    }
}
