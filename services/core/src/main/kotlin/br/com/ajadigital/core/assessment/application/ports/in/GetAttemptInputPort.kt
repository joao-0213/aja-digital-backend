package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.GetAttemptQuery
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface GetAttemptInputPort {
    fun attempt(request: GetAttemptQuery): AttemptRecord
}
