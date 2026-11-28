package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.announcements.application.ports.`in`.ListCalendarEventsInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.models.ListCalendarEventsQuery
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementCalendarQuery
import br.com.ajadigital.core.shared.application.contracts.Page

class ListCalendarEventsUseCase(
    private val store: AnnouncementStoreOutputPort,
) : ListCalendarEventsInputPort {
    override fun calendar(request: ListCalendarEventsQuery): Page<AnnouncementCalendarEvent> {
        val (actor, page, size, sort, from, to, subjectId) = request
                AnnouncementsPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(page > 0 && size in 1..100 && sort in setOf("date", "-date"))
        val start = from?.let(AnnouncementsPolicy::parseDate)
        val end = to?.let(AnnouncementsPolicy::parseDate)
        require(start == null || end == null || !start.isAfter(end))
        return store.calendar(ListAnnouncementCalendarQuery(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, start?.toString(), end?.toString(), subjectId))
    }
}
