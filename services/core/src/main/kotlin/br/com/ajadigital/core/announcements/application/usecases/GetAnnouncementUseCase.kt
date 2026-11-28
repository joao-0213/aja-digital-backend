package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.announcements.application.contracts.Announcement

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.announcements.application.ports.`in`.GetAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.models.GetAnnouncementQuery
import br.com.ajadigital.core.announcements.application.models.GetStoredAnnouncementQuery
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementNotFound

class GetAnnouncementUseCase(
    private val store: AnnouncementStoreOutputPort,
) : GetAnnouncementInputPort {
    override fun announcement(request: GetAnnouncementQuery): Announcement = request.let {
        val (actor, id) = request
        AnnouncementsPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null).let {
            store.announcement(GetStoredAnnouncementQuery(actor.id, actor.role, actor.sessionValidationReference, id)) ?: throw AnnouncementNotFound()
        }
    }
}
