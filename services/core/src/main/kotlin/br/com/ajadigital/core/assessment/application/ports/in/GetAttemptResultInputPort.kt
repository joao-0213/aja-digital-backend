package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.GetAttemptResultDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface GetAttemptResultInputPort {
    fun result(request: GetAttemptResultDto): Map<String, Any?>
}
