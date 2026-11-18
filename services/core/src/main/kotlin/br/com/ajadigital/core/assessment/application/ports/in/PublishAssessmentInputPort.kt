package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.PublishAssessmentDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface PublishAssessmentInputPort {
    fun publish(request: PublishAssessmentDto): Map<String, Any?>
}
