package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent

import br.com.ajadigital.core.announcements.application.models.ListCalendarEventsQuery
import br.com.ajadigital.core.shared.application.contracts.Page

interface ListCalendarEventsInputPort {
    fun calendar(request: ListCalendarEventsQuery): Page<AnnouncementCalendarEvent>
}
