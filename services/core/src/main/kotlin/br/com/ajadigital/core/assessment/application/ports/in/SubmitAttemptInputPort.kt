package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.SubmitAttemptCommand
import br.com.ajadigital.core.assessment.domain.models.AttemptResult

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface SubmitAttemptInputPort {
    fun submit(request: SubmitAttemptCommand): AttemptResult
}
