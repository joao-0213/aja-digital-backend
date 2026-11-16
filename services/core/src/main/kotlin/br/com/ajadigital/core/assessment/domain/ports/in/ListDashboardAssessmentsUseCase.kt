package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ListDashboardAssessmentsUseCase {
    fun dashboardAssessments(actor: AcademicActor, pageSize: Int = 100): Map<String, Any?>
}
