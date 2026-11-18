package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreCreateDto

import br.com.ajadigital.core.assessment.domain.models.SubmissionStoreCreateDto

import br.com.ajadigital.core.assessment.application.models.CreateSubmissionDto

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.CreateSubmissionInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class CreateSubmissionUseCase(private val store: SubmissionStoreOutputPort, private val mapper: JsonSerializerOutputPort) : CreateSubmissionInputPort {
    override fun create(request: CreateSubmissionDto): Map<String, Any?> {
                val (actor, key, body) = request
        SubmissionPolicy.student(actor)
        SubmissionPolicy.validKey(key)
        require(SubmissionPolicy.isObject(body) && SubmissionPolicy.fields(body) == setOf("assessmentId", "attachments"))
        val assessmentId = SubmissionPolicy.text(body, "assessmentId").also(SubmissionPolicy::id)
        val attachments = SubmissionPolicy.attachments(SubmissionPolicy.value(body, "attachments"))
        return store.create(SubmissionStoreCreateDto(actor.id, actor.role, actor.sessionValidationReference, assessmentId, attachments, key, SubmissionPolicy.hash(mapper, body)))
    }
}
