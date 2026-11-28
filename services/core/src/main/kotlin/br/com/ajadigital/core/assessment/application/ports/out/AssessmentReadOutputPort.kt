package br.com.ajadigital.core.assessment.application.ports.out

import br.com.ajadigital.core.assessment.application.contracts.DashboardAssessment
import br.com.ajadigital.core.assessment.application.contracts.ListDashboardAssessmentsQuery
import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.AssessmentResults
import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery
import br.com.ajadigital.core.assessment.application.models.ListAssessmentsQuery
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface AssessmentReadOutputPort {
    fun assessment(request: GetAssessmentQuery): AssessmentDetails?

    fun assessments(request: ListAssessmentsQuery): AssessmentResults

    fun dashboardAssessments(query: ListDashboardAssessmentsQuery): List<DashboardAssessment>

    fun pendingStudentCount(actor: AuthenticatedActor): Int
}
