package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent
import br.com.ajadigital.core.announcements.application.contracts.ListDashboardUpcomingEventsQuery

interface ListDashboardUpcomingEventsInputPort {
    fun dashboardUpcoming(request: ListDashboardUpcomingEventsQuery): List<AnnouncementCalendarEvent>
}
