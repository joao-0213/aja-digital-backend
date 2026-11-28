package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent
import br.com.ajadigital.core.announcements.application.contracts.ListDashboardUpcomingEventsQuery
import br.com.ajadigital.core.announcements.application.models.ListDashboardUpcomingStoreQuery
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardUpcomingEventsInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy

class ListDashboardUpcomingEventsUseCase(
    private val store: AnnouncementStoreOutputPort,
) : ListDashboardUpcomingEventsInputPort {
    override fun dashboardUpcoming(request: ListDashboardUpcomingEventsQuery): List<AnnouncementCalendarEvent> = request.let {
        val actor = request.actor
        val from = request.from
        val to = request.to
        val size = request.size
        AnnouncementsPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null).let {
            store.dashboardUpcoming(ListDashboardUpcomingStoreQuery(actor.id, actor.role, actor.sessionValidationReference, from.toString(), to.toString(), size))
        }
    }
}
