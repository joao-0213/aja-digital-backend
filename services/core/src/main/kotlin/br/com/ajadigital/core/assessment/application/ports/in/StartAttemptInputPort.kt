package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.StartAttemptDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface StartAttemptInputPort {
    fun start(request: StartAttemptDto): Map<String, Any?>
}
