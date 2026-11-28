package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.announcements.application.contracts.Announcement

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardAnnouncementsInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.contracts.ListDashboardAnnouncementsQuery
import br.com.ajadigital.core.announcements.application.models.ListDashboardAnnouncementStoreQuery

class ListDashboardAnnouncementsUseCase(
    private val store: AnnouncementStoreOutputPort,
) : ListDashboardAnnouncementsInputPort {
    override fun dashboardAnnouncements(request: ListDashboardAnnouncementsQuery): List<Announcement> = request.let {
        val (actor, size) = request
        AnnouncementsPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null).let {
            store.dashboardAnnouncements(ListDashboardAnnouncementStoreQuery(actor.id, actor.role, actor.sessionValidationReference, size))
        }
    }
}
