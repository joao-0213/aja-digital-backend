package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.CreateAssessmentUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class CreateAssessment(
    private val store: AssessmentStore,
    private val snapshots: QuestionSnapshotProvider,
    private val idempotency: IdempotencyStore,
    private val mapper: ObjectMapper,
) : CreateAssessmentUseCase {
    override fun create(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?> {
        AssessmentSupport.writer(actor)
        AssessmentSupport.validKey(key)
        val draft = AssessmentSupport.draft(body)
        val questions = AssessmentSupport.entries(body.get("questions"), draft.kind, null)
        store.authorizeWrite(actor.id, actor.role, actor.sessionTokenHash, draft.moduleId)
        val hash = AssessmentSupport.requestHash(mapper, body)
        AssessmentSupport.findReplay(idempotency, actor, "createAssessment", "/assessments", key, hash)?.let { return it }
        val prepared = AssessmentSupport.capture(snapshots, questions, emptySet())
        return store.create(actor.id, actor.role, actor.sessionTokenHash, key, hash, draft, prepared)
    }
}
