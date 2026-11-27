package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.PublishAssessmentCommand

interface PublishAssessmentInputPort {
    fun publish(request: PublishAssessmentCommand): AssessmentDetails.Staff
}
