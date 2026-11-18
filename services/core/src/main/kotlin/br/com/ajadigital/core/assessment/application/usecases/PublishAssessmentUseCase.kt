package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AssessmentStorePublishDto

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreAssessmentDto

import br.com.ajadigital.core.assessment.application.models.PublishAssessmentDto

import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishAssessmentInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class PublishAssessmentUseCase(
    private val store: AssessmentStoreOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
) : PublishAssessmentInputPort {
    override fun publish(request: PublishAssessmentDto): Map<String, Any?> {
                val (actor, id, key, expectedRevision, body) = request
        AssessmentPolicy.writer(actor)
        AssessmentPolicy.id(id)
        require(expectedRevision > 0)
        AssessmentPolicy.validKey(key)
        val hash = AssessmentPolicy.requestHash(mapper, body)
        require(AssessmentPolicy.isObject(body) && AssessmentPolicy.fieldNames(body) == setOf("expectedRevision"))
        store.assessment(AssessmentStoreAssessmentDto(actor.id, actor.role, actor.sessionValidationReference, id)) ?: throw AcademicNotFound()
        AssessmentPolicy.findReplay(idempotency, actor, "publishAssessment", "/assessments/$id/publish", key, hash)?.let { return it }
        return store.publish(AssessmentStorePublishDto(actor.id, actor.role, actor.sessionValidationReference, id, key, hash, expectedRevision))
            ?: throw AcademicConflict(store.assessment(AssessmentStoreAssessmentDto(actor.id, actor.role, actor.sessionValidationReference, id))?.get("revision") as? Int)
    }
}
