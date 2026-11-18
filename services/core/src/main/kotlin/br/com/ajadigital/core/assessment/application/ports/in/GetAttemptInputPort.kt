package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.GetAttemptDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface GetAttemptInputPort {
    fun attempt(request: GetAttemptDto): Map<String, Any?>
}
