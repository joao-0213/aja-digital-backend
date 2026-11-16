package br.com.ajadigital.core.announcements.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import java.time.LocalDate

interface ListDashboardUpcomingEventsUseCase {
    fun dashboardUpcoming(actor: AcademicActor, from: LocalDate, to: LocalDate, size: Int = 5): List<Map<String, Any?>>
}
