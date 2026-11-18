package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardAnnouncementsInputPort
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.models.ListDashboardAnnouncementsDto
import br.com.ajadigital.core.announcements.domain.models.ListDashboardAnnouncementRecordsDto

class ListDashboardAnnouncementsUseCase(
    private val store: AnnouncementStoreOutputPort,
) : ListDashboardAnnouncementsInputPort {
    override fun dashboardAnnouncements(request: ListDashboardAnnouncementsDto): List<Map<String, Any?>> = request.let {
        val (actor, size) = request
        AnnouncementsPolicy.requireReader(actor).let {
            store.dashboardAnnouncements(ListDashboardAnnouncementRecordsDto(actor.id, actor.role, actor.sessionValidationReference, size))
        }
    }
}
