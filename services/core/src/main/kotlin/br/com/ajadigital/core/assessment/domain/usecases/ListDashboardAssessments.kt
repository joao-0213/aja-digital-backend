package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.ListDashboardAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class ListDashboardAssessments(private val store: AssessmentStore) : ListDashboardAssessmentsUseCase {
    override fun dashboardAssessments(actor: AcademicActor, pageSize: Int): Map<String, Any?> {
        AssessmentSupport.reader(actor)
        val page = store.dashboardAssessments(actor.id, actor.role, actor.sessionTokenHash, pageSize)
        return mapOf("items" to page.items, "page" to page.page, "pageSize" to page.pageSize, "total" to page.total)
    }
}
