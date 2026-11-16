package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.policies.AssessmentEditPolicy
import br.com.ajadigital.core.assessment.domain.ports.`in`.UpdateAssessmentUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class UpdateAssessment(
    private val store: AssessmentStore,
    private val snapshots: QuestionSnapshotProvider,
    private val idempotency: IdempotencyStore,
    private val mapper: ObjectMapper,
) : UpdateAssessmentUseCase {
    override fun update(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?> {
        AssessmentSupport.writer(actor)
        AssessmentSupport.id(id)
        AssessmentSupport.validKey(key)
        val hash = AssessmentSupport.requestHash(mapper, body)
        val expectedRevision = body.get("expectedRevision")?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue()?.takeIf { it > 0 } ?: throw IllegalArgumentException()
        val current = store.assessment(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
        AssessmentSupport.findReplay(idempotency, actor, "updateAssessment", "/assessments/$id", key, hash)?.let { return it }
        val questionsSupplied = body.has("questions")
        val requestedDraft = AssessmentSupport.partialDraft(body, current)
        store.authorizeWrite(actor.id, actor.role, actor.sessionTokenHash, current["moduleId"] as String)
        if (requestedDraft.moduleId != current["moduleId"]) store.authorizeWrite(actor.id, actor.role, actor.sessionTokenHash, requestedDraft.moduleId)
        val questions = if (questionsSupplied) AssessmentSupport.entries(body.get("questions"), requestedDraft.kind, current) else null
        val effectiveQuestionCount = questions?.size ?: (current["questionIds"] as? List<*>)?.size ?: 0
        if (!AssessmentEditPolicy.isValidKindQuestionCount(requestedDraft.kind, effectiveQuestionCount)) {
            throw AcademicConflict(current["revision"] as? Int)
        }
        val reset = body.get("confirmResetDrafts")?.booleanValue()?.also { require(body.get("confirmResetDrafts").isBoolean) } ?: false
        if (questions != null && AssessmentSupport.questionsChanged(current, questions) && !reset) throw AcademicConflict(current["revision"] as? Int)
        val existingIds = (current["questionIds"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
        val prepared = questions?.let { AssessmentSupport.capture(snapshots, it, existingIds) }
        return store.update(actor.id, actor.role, actor.sessionTokenHash, id, key, hash, expectedRevision, requestedDraft, prepared, reset)
            ?: throw AcademicConflict(current["revision"] as? Int)
    }
}
