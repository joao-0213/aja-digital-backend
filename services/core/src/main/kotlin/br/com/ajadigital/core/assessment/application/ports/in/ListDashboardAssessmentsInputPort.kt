package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.contracts.DashboardAssessment
import br.com.ajadigital.core.assessment.application.contracts.ListDashboardAssessmentsQuery

interface ListDashboardAssessmentsInputPort {
    fun dashboardAssessments(query: ListDashboardAssessmentsQuery): List<DashboardAssessment>
}
