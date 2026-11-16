package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ListDashboardTopicsUseCase {
    fun dashboardTopics(actor: AcademicActor): List<Map<String, Any?>>
}
