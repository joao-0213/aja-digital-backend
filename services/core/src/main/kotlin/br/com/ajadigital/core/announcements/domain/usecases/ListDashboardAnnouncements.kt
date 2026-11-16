package br.com.ajadigital.core.announcements.domain.usecases

import br.com.ajadigital.core.announcements.domain.ports.`in`.ListDashboardAnnouncementsUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class ListDashboardAnnouncements(
    private val store: AnnouncementStore,
) : ListDashboardAnnouncementsUseCase {
    override fun dashboardAnnouncements(actor: AcademicActor, size: Int): List<Map<String, Any?>> =
        AnnouncementsSupport.requireReader(actor).let {
            store.dashboardAnnouncements(actor.id, actor.role, actor.sessionTokenHash, size)
        }
}
