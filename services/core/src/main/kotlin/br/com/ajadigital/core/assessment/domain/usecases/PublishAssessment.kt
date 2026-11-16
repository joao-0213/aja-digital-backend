package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.PublishAssessmentUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class PublishAssessment(
    private val store: AssessmentStore,
    private val idempotency: IdempotencyStore,
    private val mapper: ObjectMapper,
) : PublishAssessmentUseCase {
    override fun publish(actor: AcademicActor, id: String, key: String, expectedRevision: Int, body: JsonNode): Map<String, Any?> {
        AssessmentSupport.writer(actor)
        AssessmentSupport.id(id)
        require(expectedRevision > 0)
        AssessmentSupport.validKey(key)
        val hash = AssessmentSupport.requestHash(mapper, body)
        require(body.isObject && AssessmentSupport.fieldNames(body) == setOf("expectedRevision"))
        store.assessment(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
        AssessmentSupport.findReplay(idempotency, actor, "publishAssessment", "/assessments/$id/publish", key, hash)?.let { return it }
        return store.publish(actor.id, actor.role, actor.sessionTokenHash, id, key, hash, expectedRevision)
            ?: throw AcademicConflict(store.assessment(actor.id, actor.role, actor.sessionTokenHash, id)?.get("revision") as? Int)
    }
}
