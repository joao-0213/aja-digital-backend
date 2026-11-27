package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.GetAttemptResultQuery
import br.com.ajadigital.core.assessment.domain.models.AttemptResult

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface GetAttemptResultInputPort {
    fun result(request: GetAttemptResultQuery): AttemptResult
}
