package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent

import br.com.ajadigital.core.announcements.application.models.ListAnnouncementsQuery
import br.com.ajadigital.core.shared.application.contracts.Page

interface ListAnnouncementsInputPort {
    fun announcements(request: ListAnnouncementsQuery): Page<Announcement>
}
