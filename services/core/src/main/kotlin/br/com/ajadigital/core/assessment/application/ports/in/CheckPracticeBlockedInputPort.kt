package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.CheckPracticeBlockedDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface CheckPracticeBlockedInputPort {
    fun practiceBlocked(request: CheckPracticeBlockedDto): Boolean
}
