package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.SaveSubmissionFeedbackCommand
import br.com.ajadigital.core.assessment.domain.models.SubmissionView

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface SaveSubmissionFeedbackInputPort {
    fun saveFeedback(request: SaveSubmissionFeedbackCommand): SubmissionView
}
