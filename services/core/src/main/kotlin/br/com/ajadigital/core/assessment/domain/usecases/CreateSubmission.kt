package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.CreateSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class CreateSubmission(private val store: SubmissionStore, private val mapper: ObjectMapper) : CreateSubmissionUseCase {
    override fun create(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?> {
        SubmissionSupport.student(actor)
        SubmissionSupport.validKey(key)
        require(body.isObject && SubmissionSupport.fields(body) == setOf("assessmentId", "attachments"))
        val assessmentId = SubmissionSupport.text(body, "assessmentId").also(SubmissionSupport::id)
        val attachments = SubmissionSupport.attachments(body.get("attachments"))
        return store.create(actor.id, actor.role, actor.sessionTokenHash, assessmentId, attachments, key, SubmissionSupport.hash(mapper, body))
    }
}
