package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.application.models.ListDashboardAnnouncementsDto

interface ListDashboardAnnouncementsInputPort {
    fun dashboardAnnouncements(request: ListDashboardAnnouncementsDto): List<Map<String, Any?>>
}
