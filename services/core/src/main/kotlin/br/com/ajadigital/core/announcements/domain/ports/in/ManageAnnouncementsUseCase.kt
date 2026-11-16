package br.com.ajadigital.core.announcements.domain.ports.`in`

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import java.time.LocalDate

interface ManageAnnouncementsUseCase {
    fun create(actor: AcademicActor, draft: AnnouncementDraft, key: String): Map<String, Any?>
    fun update(actor: AcademicActor, id: String, revision: Int, draft: AnnouncementDraft): Map<String, Any?>
    fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?>
    fun announcement(actor: AcademicActor, id: String): Map<String, Any?>
    fun announcements(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?): AcademicPage
    fun calendar(actor: AcademicActor, page: Int, size: Int, sort: String, from: String?, to: String?, subjectId: String?): AcademicPage
    fun dashboardAnnouncements(actor: AcademicActor, size: Int = 5): List<Map<String, Any?>>
    fun dashboardUpcoming(actor: AcademicActor, from: LocalDate, to: LocalDate, size: Int = 5): List<Map<String, Any?>>
}
