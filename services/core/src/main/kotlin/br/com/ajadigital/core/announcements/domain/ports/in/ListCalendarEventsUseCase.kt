package br.com.ajadigital.core.announcements.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListCalendarEventsUseCase {
    fun calendar(actor: AcademicActor, page: Int, size: Int, sort: String, from: String?, to: String?, subjectId: String?): AcademicPage
}
