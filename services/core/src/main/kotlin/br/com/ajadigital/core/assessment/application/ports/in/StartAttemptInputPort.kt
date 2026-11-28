package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.StartAttemptCommand
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface StartAttemptInputPort {
    fun start(request: StartAttemptCommand): AttemptRecord
}
