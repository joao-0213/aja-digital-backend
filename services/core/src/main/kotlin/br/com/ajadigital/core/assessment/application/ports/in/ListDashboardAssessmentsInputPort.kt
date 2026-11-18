package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.ListDashboardAssessmentsDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface ListDashboardAssessmentsInputPort {
    fun dashboardAssessments(request: ListDashboardAssessmentsDto): Map<String, Any?>
}
