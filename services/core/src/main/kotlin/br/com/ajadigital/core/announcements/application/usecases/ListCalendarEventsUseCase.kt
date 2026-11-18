package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.announcements.application.ports.`in`.ListCalendarEventsInputPort
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.models.ListCalendarEventsDto
import br.com.ajadigital.core.announcements.domain.models.ListAnnouncementCalendarDto
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

class ListCalendarEventsUseCase(
    private val store: AnnouncementStoreOutputPort,
) : ListCalendarEventsInputPort {
    override fun calendar(request: ListCalendarEventsDto): AcademicPage {
        val (actor, page, size, sort, from, to, subjectId) = request
                AnnouncementsPolicy.requireReader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("date", "-date"))
        val start = from?.let(AnnouncementsPolicy::parseDate)
        val end = to?.let(AnnouncementsPolicy::parseDate)
        require(start == null || end == null || !start.isAfter(end))
        return store.calendar(ListAnnouncementCalendarDto(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, start?.toString(), end?.toString(), subjectId))
    }
}
