package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.SubmissionSaveFeedbackRecord

import br.com.ajadigital.core.assessment.application.models.SaveSubmissionFeedbackCommand

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.SaveSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import java.math.BigDecimal

class SaveSubmissionFeedbackUseCase(private val store: SubmissionStoreOutputPort) : SaveSubmissionFeedbackInputPort {
    override fun saveFeedback(request: SaveSubmissionFeedbackCommand): br.com.ajadigital.core.assessment.domain.models.SubmissionView {
                val (actor, id, body) = request
        SubmissionPolicy.writer(actor.role, actor.active, actor.emailConfirmedAt != null)
        SubmissionPolicy.id(id)
        require(body.fieldsValid)
        val comment = body.comment ?: throw IllegalArgumentException()
        val grade = body.grade?.also { require(it >= BigDecimal.ZERO) }
        return store.saveFeedback(SubmissionSaveFeedbackRecord(
            actor,
            id,
            (body.expectedRevision ?: throw IllegalArgumentException()).also { require(it > 0) },
            comment,
            grade,
        )) ?: throw AssessmentNotFound()
    }
}
