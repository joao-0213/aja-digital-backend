package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery

interface GetAssessmentInputPort {
    fun assessment(request: GetAssessmentQuery): AssessmentDetails
}
