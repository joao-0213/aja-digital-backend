package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.application.models.ListDashboardUpcomingEventsDto

interface ListDashboardUpcomingEventsInputPort {
    fun dashboardUpcoming(request: ListDashboardUpcomingEventsDto): List<Map<String, Any?>>
}
