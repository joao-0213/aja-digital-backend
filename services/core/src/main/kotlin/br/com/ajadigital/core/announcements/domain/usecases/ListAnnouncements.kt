package br.com.ajadigital.core.announcements.domain.usecases

import br.com.ajadigital.core.announcements.domain.ports.`in`.ListAnnouncementsUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

class ListAnnouncements(
    private val store: AnnouncementStore,
) : ListAnnouncementsUseCase {
    override fun announcements(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?): AcademicPage {
        AnnouncementsSupport.requireReader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("createdAt", "-createdAt"))
        return store.announcements(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, classId, subjectId, query?.trim())
    }
}
