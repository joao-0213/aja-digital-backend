package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.GetAssessmentDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface GetAssessmentInputPort {
    fun assessment(request: GetAssessmentDto): Map<String, Any?>
}
