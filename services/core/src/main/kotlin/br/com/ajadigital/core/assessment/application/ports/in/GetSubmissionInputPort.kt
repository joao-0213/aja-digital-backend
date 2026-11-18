package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.GetSubmissionDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface GetSubmissionInputPort {
    fun submission(request: GetSubmissionDto): Map<String, Any?>
}
