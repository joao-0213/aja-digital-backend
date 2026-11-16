package br.com.ajadigital.core.announcements.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ListDashboardAnnouncementsUseCase {
    fun dashboardAnnouncements(actor: AcademicActor, size: Int = 5): List<Map<String, Any?>>
}
