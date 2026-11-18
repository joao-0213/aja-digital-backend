package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.CreateAssessmentDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface CreateAssessmentInputPort {
    fun create(request: CreateAssessmentDto): Map<String, Any?>
}
