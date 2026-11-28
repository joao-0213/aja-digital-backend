package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.PublishSubmissionFeedbackCommand
import br.com.ajadigital.core.assessment.domain.models.SubmissionView

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface PublishSubmissionFeedbackInputPort {
    fun publishFeedback(request: PublishSubmissionFeedbackCommand): SubmissionView
}
