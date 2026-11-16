package br.com.ajadigital.core.announcements.domain.usecases

import br.com.ajadigital.core.announcements.domain.ports.`in`.ListCalendarEventsUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

class ListCalendarEvents(
    private val store: AnnouncementStore,
) : ListCalendarEventsUseCase {
    override fun calendar(actor: AcademicActor, page: Int, size: Int, sort: String, from: String?, to: String?, subjectId: String?): AcademicPage {
        AnnouncementsSupport.requireReader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("date", "-date"))
        val start = from?.let(AnnouncementsSupport::parseDate)
        val end = to?.let(AnnouncementsSupport::parseDate)
        require(start == null || end == null || !start.isAfter(end))
        return store.calendar(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, start?.toString(), end?.toString(), subjectId)
    }
}
