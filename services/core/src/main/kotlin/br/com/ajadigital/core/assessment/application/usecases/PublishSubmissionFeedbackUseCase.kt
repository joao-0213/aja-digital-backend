package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.SubmissionStorePublishFeedbackDto

import br.com.ajadigital.core.assessment.application.models.PublishSubmissionFeedbackDto

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class PublishSubmissionFeedbackUseCase(private val store: SubmissionStoreOutputPort, private val mapper: JsonSerializerOutputPort) : PublishSubmissionFeedbackInputPort {
    override fun publishFeedback(request: PublishSubmissionFeedbackDto): Map<String, Any?> {
                val (actor, id, key, body) = request
        SubmissionPolicy.writer(actor)
        SubmissionPolicy.id(id)
        SubmissionPolicy.validKey(key)
        require(SubmissionPolicy.isObject(body) && SubmissionPolicy.fields(body) == setOf("expectedRevision"))
        return store.publishFeedback(SubmissionStorePublishFeedbackDto(
            actor.id,
            actor.role,
            actor.sessionValidationReference,
            id,
            SubmissionPolicy.integer(body, "expectedRevision").also { require(it > 0) },
            key,
            SubmissionPolicy.hash(mapper, body),
        )) ?: throw AcademicNotFound()
    }
}
