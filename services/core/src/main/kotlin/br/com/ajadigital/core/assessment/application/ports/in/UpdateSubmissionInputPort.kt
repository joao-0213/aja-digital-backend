package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.UpdateSubmissionDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface UpdateSubmissionInputPort {
    fun update(request: UpdateSubmissionDto): Map<String, Any?>
}
