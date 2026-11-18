package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreCreateDto

import br.com.ajadigital.core.assessment.domain.models.SubmissionStoreCreateDto

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreAuthorizeWriteDto

import br.com.ajadigital.core.assessment.application.models.CreateAssessmentDto

import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.CreateAssessmentInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStoreOutputPort
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class CreateAssessmentUseCase(
    private val store: AssessmentStoreOutputPort,
    private val snapshots: QuestionSnapshotProviderOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
) : CreateAssessmentInputPort {
    override fun create(request: CreateAssessmentDto): Map<String, Any?> {
                val (actor, key, body) = request
        AssessmentPolicy.writer(actor)
        AssessmentPolicy.validKey(key)
        val draft = AssessmentPolicy.draft(body)
        val questions = AssessmentPolicy.entries(AssessmentPolicy.value(body, "questions"), draft.kind, null)
        store.authorizeWrite(AssessmentStoreAuthorizeWriteDto(actor.id, actor.role, actor.sessionValidationReference, draft.moduleId))
        val hash = AssessmentPolicy.requestHash(mapper, body)
        AssessmentPolicy.findReplay(idempotency, actor, "createAssessment", "/assessments", key, hash)?.let { return it }
        val prepared = AssessmentPolicy.capture(snapshots, questions, emptySet())
        return store.create(AssessmentStoreCreateDto(actor.id, actor.role, actor.sessionValidationReference, key, hash, draft, prepared))
    }
}
