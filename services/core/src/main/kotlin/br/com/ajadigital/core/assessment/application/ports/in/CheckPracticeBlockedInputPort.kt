package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.CheckPracticeBlockedQuery

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface CheckPracticeBlockedInputPort {
    fun practiceBlocked(request: CheckPracticeBlockedQuery): Boolean
}
