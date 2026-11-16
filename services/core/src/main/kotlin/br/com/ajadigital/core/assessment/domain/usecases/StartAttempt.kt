package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.StartAttemptUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class StartAttempt(private val store: AttemptStore, private val mapper: ObjectMapper) : StartAttemptUseCase {
    override fun start(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?> {
        AttemptSupport.student(actor)
        AttemptSupport.validKey(key)
        require(body.isObject && AttemptSupport.fields(body) == setOf("assessmentId"))
        val assessmentId = AttemptSupport.text(body, "assessmentId").also(AttemptSupport::id)
        return store.start(actor.id, actor.role, actor.sessionTokenHash, assessmentId, key, AttemptSupport.hash(mapper, body))
    }
}
