package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.contracts.DashboardAssessment
import br.com.ajadigital.core.assessment.application.contracts.ListDashboardAssessmentsQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.ListDashboardAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import java.util.Collections

class ListDashboardAssessmentsUseCase(private val store: AssessmentReadOutputPort) : ListDashboardAssessmentsInputPort {
    override fun dashboardAssessments(query: ListDashboardAssessmentsQuery): List<DashboardAssessment> {
        AssessmentPolicy.reader(query.actor.role, query.actor.active, query.actor.emailConfirmedAt != null)
        require(query.pageSize in 1..100)
        return Collections.unmodifiableList(store.dashboardAssessments(query).toList())
    }
}
