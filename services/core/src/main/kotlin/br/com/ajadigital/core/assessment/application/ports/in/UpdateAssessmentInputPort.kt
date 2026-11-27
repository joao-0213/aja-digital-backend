package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentCommand

interface UpdateAssessmentInputPort {
    fun update(request: UpdateAssessmentCommand): AssessmentDetails.Staff
}
