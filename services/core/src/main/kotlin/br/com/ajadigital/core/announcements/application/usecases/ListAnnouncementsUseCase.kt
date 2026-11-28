package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementsQuery
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementsStoreQuery
import br.com.ajadigital.core.announcements.application.ports.`in`.ListAnnouncementsInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.shared.application.contracts.Page

class ListAnnouncementsUseCase(
    private val store: AnnouncementStoreOutputPort,
) : ListAnnouncementsInputPort {
    override fun announcements(request: ListAnnouncementsQuery): Page<Announcement> {
        val actor = request.actor
        val page = request.page
        val size = request.size
        val sort = request.sort
        val classId = request.classId
        val subjectId = request.subjectId
        val query = request.query
        AnnouncementsPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(page > 0 && size in 1..MAX_PAGE_SIZE && sort in setOf("createdAt", "-createdAt"))
        return store.announcements(ListAnnouncementsStoreQuery(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, classId, subjectId, query?.trim()))
    }
}

private const val MAX_PAGE_SIZE = 100
