package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.UpdateSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

class UpdateSubmission(private val store: SubmissionStore) : UpdateSubmissionUseCase {
    override fun update(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?> {
        SubmissionSupport.student(actor)
        SubmissionSupport.id(id)
        require(body.isObject && SubmissionSupport.fields(body).all { it in setOf("expectedRevision", "attachments") } && body.has("expectedRevision"))
        val revision = SubmissionSupport.integer(body, "expectedRevision").also { require(it > 0) }
        val attachments = if (body.has("attachments")) SubmissionSupport.attachments(body.get("attachments")) else null
        return store.update(actor.id, actor.role, actor.sessionTokenHash, id, revision, attachments) ?: throw AcademicNotFound()
    }
}
