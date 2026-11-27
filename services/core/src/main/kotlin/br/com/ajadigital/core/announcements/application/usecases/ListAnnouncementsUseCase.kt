package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.announcements.application.ports.`in`.ListAnnouncementsInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementsQuery
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementsStoreQuery
import br.com.ajadigital.core.shared.application.contracts.Page

class ListAnnouncementsUseCase(
    private val store: AnnouncementStoreOutputPort,
) : ListAnnouncementsInputPort {
    override fun announcements(request: ListAnnouncementsQuery): Page<Announcement> {
        val (actor, page, size, sort, classId, subjectId, query) = request
                AnnouncementsPolicy.requireReader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("createdAt", "-createdAt"))
        return store.announcements(ListAnnouncementsStoreQuery(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, classId, subjectId, query?.trim()))
    }
}
