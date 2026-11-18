package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.SubmitSubmissionDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface SubmitSubmissionInputPort {
    fun submit(request: SubmitSubmissionDto): Map<String, Any?>
}
