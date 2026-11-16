package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.SubmitAttemptUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class SubmitAttempt(private val store: AttemptStore, private val mapper: ObjectMapper) : SubmitAttemptUseCase {
    override fun submit(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?> {
        AttemptSupport.student(actor)
        AttemptSupport.id(id)
        AttemptSupport.validKey(key)
        require(body.isObject && AttemptSupport.fields(body) == setOf("expectedRevision"))
        val revision = AttemptSupport.integer(body, "expectedRevision").also { require(it > 0) }
        return store.submit(actor.id, actor.role, actor.sessionTokenHash, id, key, AttemptSupport.hash(mapper, body), revision) ?: throw AcademicNotFound()
    }
}
