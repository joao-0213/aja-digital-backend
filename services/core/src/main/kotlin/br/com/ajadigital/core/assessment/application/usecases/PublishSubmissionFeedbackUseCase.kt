package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.SubmissionPublishFeedbackRecord

import br.com.ajadigital.core.assessment.application.models.PublishSubmissionFeedbackCommand

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class PublishSubmissionFeedbackUseCase(private val store: SubmissionStoreOutputPort) : PublishSubmissionFeedbackInputPort {
    override fun publishFeedback(request: PublishSubmissionFeedbackCommand): br.com.ajadigital.core.assessment.domain.models.SubmissionView {
                val (actor, id, key, body) = request
        SubmissionPolicy.writer(actor.role, actor.active, actor.emailConfirmedAt != null)
        SubmissionPolicy.id(id)
        SubmissionPolicy.validKey(key)
        require(body.fieldsValid)
        return store.publishFeedback(SubmissionPublishFeedbackRecord(
            actor,
            id,
            (body.expectedRevision ?: throw IllegalArgumentException()).also { require(it > 0) },
            key,
            request.requestHash,
        )) ?: throw AssessmentNotFound()
    }
}
