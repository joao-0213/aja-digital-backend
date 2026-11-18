package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.PublishSubmissionFeedbackDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface PublishSubmissionFeedbackInputPort {
    fun publishFeedback(request: PublishSubmissionFeedbackDto): Map<String, Any?>
}
