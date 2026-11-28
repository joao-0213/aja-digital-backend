package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.contracts.CountPendingFeedbackQuery
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface CountPendingFeedbackInputPort {
    fun pendingFeedbackCount(request: CountPendingFeedbackQuery): Int
}
