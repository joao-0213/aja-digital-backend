package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.CountPendingFeedbackDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface CountPendingFeedbackInputPort {
    fun pendingFeedbackCount(request: CountPendingFeedbackDto): Int
}
