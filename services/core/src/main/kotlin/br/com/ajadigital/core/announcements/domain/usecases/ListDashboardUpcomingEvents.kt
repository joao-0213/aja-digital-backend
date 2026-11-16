package br.com.ajadigital.core.announcements.domain.usecases

import br.com.ajadigital.core.announcements.domain.ports.`in`.ListDashboardUpcomingEventsUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import java.time.LocalDate

class ListDashboardUpcomingEvents(
    private val store: AnnouncementStore,
) : ListDashboardUpcomingEventsUseCase {
    override fun dashboardUpcoming(actor: AcademicActor, from: LocalDate, to: LocalDate, size: Int): List<Map<String, Any?>> =
        AnnouncementsSupport.requireReader(actor).let {
            store.dashboardUpcoming(actor.id, actor.role, actor.sessionTokenHash, from.toString(), to.toString(), size)
        }
}
