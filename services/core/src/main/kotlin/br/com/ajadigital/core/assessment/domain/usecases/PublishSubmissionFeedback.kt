package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.PublishSubmissionFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class PublishSubmissionFeedback(private val store: SubmissionStore, private val mapper: ObjectMapper) : PublishSubmissionFeedbackUseCase {
    override fun publishFeedback(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?> {
        SubmissionSupport.writer(actor)
        SubmissionSupport.id(id)
        SubmissionSupport.validKey(key)
        require(body.isObject && SubmissionSupport.fields(body) == setOf("expectedRevision"))
        return store.publishFeedback(
            actor.id,
            actor.role,
            actor.sessionTokenHash,
            id,
            SubmissionSupport.integer(body, "expectedRevision").also { require(it > 0) },
            key,
            SubmissionSupport.hash(mapper, body),
        ) ?: throw AcademicNotFound()
    }
}
