package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.SaveSubmissionFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode
import java.math.BigDecimal

class SaveSubmissionFeedback(private val store: SubmissionStore) : SaveSubmissionFeedbackUseCase {
    override fun saveFeedback(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?> {
        SubmissionSupport.writer(actor)
        SubmissionSupport.id(id)
        require(body.isObject && SubmissionSupport.fields(body) == setOf("expectedRevision", "comment", "grade"))
        val comment = SubmissionSupport.text(body, "comment")
        val gradeNode = body.get("grade")
        val grade = when {
            gradeNode == null || gradeNode.isNull -> null
            gradeNode.isNumber -> gradeNode.decimalValue().also { require(it >= BigDecimal.ZERO) }
            else -> throw IllegalArgumentException()
        }
        return store.saveFeedback(
            actor.id,
            actor.role,
            actor.sessionTokenHash,
            id,
            SubmissionSupport.integer(body, "expectedRevision").also { require(it > 0) },
            comment,
            grade,
        ) ?: throw AcademicNotFound()
    }
}
