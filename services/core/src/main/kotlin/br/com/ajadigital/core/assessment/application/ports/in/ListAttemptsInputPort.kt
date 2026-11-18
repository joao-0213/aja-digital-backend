package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.ListAttemptsDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface ListAttemptsInputPort {
    fun attempts(request: ListAttemptsDto): Map<String, Any?>
}
