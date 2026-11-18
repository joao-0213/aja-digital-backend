package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.application.models.ListCalendarEventsDto
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

interface ListCalendarEventsInputPort {
    fun calendar(request: ListCalendarEventsDto): AcademicPage
}
