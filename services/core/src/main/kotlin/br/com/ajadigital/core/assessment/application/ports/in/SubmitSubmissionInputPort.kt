package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.SubmitSubmissionCommand
import br.com.ajadigital.core.assessment.domain.models.SubmissionView
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface SubmitSubmissionInputPort {
    fun submit(request: SubmitSubmissionCommand): SubmissionView
}
