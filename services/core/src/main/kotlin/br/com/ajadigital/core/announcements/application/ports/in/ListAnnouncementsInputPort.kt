package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.application.models.ListAnnouncementsDto
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

interface ListAnnouncementsInputPort {
    fun announcements(request: ListAnnouncementsDto): AcademicPage
}
