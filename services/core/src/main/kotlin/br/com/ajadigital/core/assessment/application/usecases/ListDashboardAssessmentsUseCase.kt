package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreDashboardAssessmentsDto

import br.com.ajadigital.core.assessment.application.models.ListDashboardAssessmentsDto

import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.ListDashboardAssessmentsInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStoreOutputPort
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class ListDashboardAssessmentsUseCase(private val store: AssessmentStoreOutputPort) : ListDashboardAssessmentsInputPort {
    override fun dashboardAssessments(request: ListDashboardAssessmentsDto): Map<String, Any?> {
                val (actor, pageSize) = request
        AssessmentPolicy.reader(actor)
        val page = store.dashboardAssessments(AssessmentStoreDashboardAssessmentsDto(actor.id, actor.role, actor.sessionValidationReference, pageSize))
        return mapOf("items" to page.items, "page" to page.page, "pageSize" to page.pageSize, "total" to page.total)
    }
}
