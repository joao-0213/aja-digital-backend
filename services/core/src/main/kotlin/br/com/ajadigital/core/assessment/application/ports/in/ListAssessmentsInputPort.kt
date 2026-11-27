package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.AssessmentResults
import br.com.ajadigital.core.assessment.application.models.ListAssessmentsQuery

interface ListAssessmentsInputPort {
    fun assessments(request: ListAssessmentsQuery): AssessmentResults
}
