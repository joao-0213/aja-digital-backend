package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.ListAttemptsQuery
import br.com.ajadigital.core.assessment.domain.models.AttemptPage

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface ListAttemptsInputPort {
    fun attempts(request: ListAttemptsQuery): AttemptPage
}
