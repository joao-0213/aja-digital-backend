package br.com.ajadigital.core.announcements.domain.usecases

import br.com.ajadigital.core.announcements.domain.ports.`in`.GetAnnouncementUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class GetAnnouncement(
    private val store: AnnouncementStore,
) : GetAnnouncementUseCase {
    override fun announcement(actor: AcademicActor, id: String): Map<String, Any?> =
        AnnouncementsSupport.requireReader(actor).let {
            store.announcement(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
        }
}
