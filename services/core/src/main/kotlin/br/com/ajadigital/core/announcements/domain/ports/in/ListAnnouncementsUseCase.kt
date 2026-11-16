package br.com.ajadigital.core.announcements.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListAnnouncementsUseCase {
    fun announcements(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?): AcademicPage
}
