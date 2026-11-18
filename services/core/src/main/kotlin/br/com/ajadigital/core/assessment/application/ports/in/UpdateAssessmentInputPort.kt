package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface UpdateAssessmentInputPort {
    fun update(request: UpdateAssessmentDto): Map<String, Any?>
}
