package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent

import br.com.ajadigital.core.announcements.application.contracts.ListDashboardAnnouncementsQuery

interface ListDashboardAnnouncementsInputPort {
    fun dashboardAnnouncements(request: ListDashboardAnnouncementsQuery): List<Announcement>
}
