package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.SubmissionStoreUpdateDto

import br.com.ajadigital.core.assessment.application.models.UpdateSubmissionDto

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateSubmissionInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class UpdateSubmissionUseCase(private val store: SubmissionStoreOutputPort) : UpdateSubmissionInputPort {
    override fun update(request: UpdateSubmissionDto): Map<String, Any?> {
                val (actor, id, body) = request
        SubmissionPolicy.student(actor)
        SubmissionPolicy.id(id)
        require(SubmissionPolicy.isObject(body) && SubmissionPolicy.fields(body).all { it in setOf("expectedRevision", "attachments") } && SubmissionPolicy.has(body, "expectedRevision"))
        val revision = SubmissionPolicy.integer(body, "expectedRevision").also { require(it > 0) }
        val attachments = if (SubmissionPolicy.has(body, "attachments")) SubmissionPolicy.attachments(SubmissionPolicy.value(body, "attachments")) else null
        return store.update(SubmissionStoreUpdateDto(actor.id, actor.role, actor.sessionValidationReference, id, revision, attachments)) ?: throw AcademicNotFound()
    }
}
