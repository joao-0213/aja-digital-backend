package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.SubmissionStoreSaveFeedbackDto

import br.com.ajadigital.core.assessment.application.models.SaveSubmissionFeedbackDto

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.SaveSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import java.math.BigDecimal

class SaveSubmissionFeedbackUseCase(private val store: SubmissionStoreOutputPort) : SaveSubmissionFeedbackInputPort {
    override fun saveFeedback(request: SaveSubmissionFeedbackDto): Map<String, Any?> {
                val (actor, id, body) = request
        SubmissionPolicy.writer(actor)
        SubmissionPolicy.id(id)
        require(SubmissionPolicy.isObject(body) && SubmissionPolicy.fields(body) == setOf("expectedRevision", "comment", "grade"))
        val comment = SubmissionPolicy.text(body, "comment")
        val gradeNode = SubmissionPolicy.value(body, "grade")
        val grade = when {
            gradeNode == null -> null
            gradeNode is Number -> BigDecimal(gradeNode.toString()).also { require(it >= BigDecimal.ZERO) }
            else -> throw IllegalArgumentException()
        }
        return store.saveFeedback(SubmissionStoreSaveFeedbackDto(
            actor.id,
            actor.role,
            actor.sessionValidationReference,
            id,
            SubmissionPolicy.integer(body, "expectedRevision").also { require(it > 0) },
            comment,
            grade,
        )) ?: throw AcademicNotFound()
    }
}
