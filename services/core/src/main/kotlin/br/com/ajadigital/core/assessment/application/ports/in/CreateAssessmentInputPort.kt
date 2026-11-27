package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentCommand

interface CreateAssessmentInputPort {
    fun create(request: CreateAssessmentCommand): AssessmentDetails.Staff
}
