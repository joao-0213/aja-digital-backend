package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardUpcomingEventsInputPort
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.models.ListDashboardUpcomingEventsDto
import br.com.ajadigital.core.announcements.domain.models.ListDashboardUpcomingRecordsDto

class ListDashboardUpcomingEventsUseCase(
    private val store: AnnouncementStoreOutputPort,
) : ListDashboardUpcomingEventsInputPort {
    override fun dashboardUpcoming(request: ListDashboardUpcomingEventsDto): List<Map<String, Any?>> = request.let {
        val (actor, from, to, size) = request
        AnnouncementsPolicy.requireReader(actor).let {
            store.dashboardUpcoming(ListDashboardUpcomingRecordsDto(actor.id, actor.role, actor.sessionValidationReference, from.toString(), to.toString(), size))
        }
    }
}
