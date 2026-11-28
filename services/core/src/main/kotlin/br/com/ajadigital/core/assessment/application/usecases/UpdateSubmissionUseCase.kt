package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.SubmissionUpdateRecord

import br.com.ajadigital.core.assessment.application.models.UpdateSubmissionCommand

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class UpdateSubmissionUseCase(private val store: SubmissionStoreOutputPort) : UpdateSubmissionInputPort {
    override fun update(request: UpdateSubmissionCommand): br.com.ajadigital.core.assessment.domain.models.SubmissionView {
                val (actor, id, body) = request
        SubmissionPolicy.student(actor.role, actor.active, actor.emailConfirmedAt != null)
        SubmissionPolicy.id(id)
        require(body.fieldsValid)
        val revision = (body.expectedRevision ?: throw IllegalArgumentException()).also { require(it > 0) }
        val attachments = if (body.attachmentsPresent) SubmissionPolicy.attachments(SubmissionContracts.attachments(body.attachments ?: throw IllegalArgumentException())) else null
        return store.update(SubmissionUpdateRecord(actor, id, revision, attachments)) ?: throw AssessmentNotFound()
    }
}
