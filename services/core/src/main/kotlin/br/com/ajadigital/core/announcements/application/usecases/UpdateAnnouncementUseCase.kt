package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.models.GetStoredAnnouncementQuery
import br.com.ajadigital.core.announcements.application.models.UpdateAnnouncementCommand
import br.com.ajadigital.core.announcements.application.models.UpdateAnnouncementRecord
import br.com.ajadigital.core.announcements.application.ports.`in`.UpdateAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementConflict
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementNotFound
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class UpdateAnnouncementUseCase(
    private val store: AnnouncementStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateAnnouncementInputPort {
    override fun update(request: UpdateAnnouncementCommand): Announcement = transactionRunner.run {
        val actor = request.actor
        val id = request.id
        val revision = request.revision
        val draft = request.draft
        AnnouncementsPolicy.requireWriter(actor.role, actor.active)
        require(revision > 0)
        AnnouncementsPolicy.validate(draft)
        store.update(UpdateAnnouncementRecord(actor.id, actor.role, actor.sessionValidationReference, id, revision, draft))
            ?: store.announcement(GetStoredAnnouncementQuery(actor.id, actor.role, actor.sessionValidationReference, id))?.let { throw AnnouncementConflict(it.revision) }
            ?: throw AnnouncementNotFound()
    }
}
