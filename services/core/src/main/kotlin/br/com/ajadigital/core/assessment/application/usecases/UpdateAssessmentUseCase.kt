package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.SubmissionStoreUpdateDto

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreUpdateDto

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreAssessmentDto

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreAuthorizeWriteDto

import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentDto

import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.domain.policies.AssessmentEditPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateAssessmentInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStoreOutputPort
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class UpdateAssessmentUseCase(
    private val store: AssessmentStoreOutputPort,
    private val snapshots: QuestionSnapshotProviderOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
) : UpdateAssessmentInputPort {
    override fun update(request: UpdateAssessmentDto): Map<String, Any?> {
                val (actor, id, key, body) = request
        AssessmentPolicy.writer(actor)
        AssessmentPolicy.id(id)
        AssessmentPolicy.validKey(key)
        val hash = AssessmentPolicy.requestHash(mapper, body)
        val expectedRevision = AssessmentPolicy.integer(body, "expectedRevision").takeIf { it > 0 } ?: throw IllegalArgumentException()
        val current = store.assessment(AssessmentStoreAssessmentDto(actor.id, actor.role, actor.sessionValidationReference, id)) ?: throw AcademicNotFound()
        AssessmentPolicy.findReplay(idempotency, actor, "updateAssessment", "/assessments/$id", key, hash)?.let { return it }
        val questionsSupplied = AssessmentPolicy.has(body, "questions")
        val requestedDraft = AssessmentPolicy.partialDraft(body, current)
        store.authorizeWrite(AssessmentStoreAuthorizeWriteDto(actor.id, actor.role, actor.sessionValidationReference, current["moduleId"] as String))
        if (requestedDraft.moduleId != current["moduleId"]) store.authorizeWrite(AssessmentStoreAuthorizeWriteDto(actor.id, actor.role, actor.sessionValidationReference, requestedDraft.moduleId))
        val questions = if (questionsSupplied) AssessmentPolicy.entries(AssessmentPolicy.value(body, "questions"), requestedDraft.kind, current) else null
        val effectiveQuestionCount = questions?.size ?: (current["questionIds"] as? List<*>)?.size ?: 0
        if (!AssessmentEditPolicy.isValidKindQuestionCount(requestedDraft.kind, effectiveQuestionCount)) {
            throw AcademicConflict(current["revision"] as? Int)
        }
        val reset = AssessmentPolicy.optionalBoolean(body, "confirmResetDrafts") ?: false
        if (questions != null && AssessmentPolicy.questionsChanged(current, questions) && !reset) throw AcademicConflict(current["revision"] as? Int)
        val existingIds = (current["questionIds"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
        val prepared = questions?.let { AssessmentPolicy.capture(snapshots, it, existingIds) }
        return store.update(AssessmentStoreUpdateDto(actor.id, actor.role, actor.sessionValidationReference, id, key, hash, expectedRevision, requestedDraft, prepared, reset))
            ?: throw AcademicConflict(current["revision"] as? Int)
    }
}
