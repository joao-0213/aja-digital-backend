package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.models.GetStoredAnnouncementQuery
import br.com.ajadigital.core.announcements.application.models.WithdrawAnnouncementCommand
import br.com.ajadigital.core.announcements.application.models.WithdrawAnnouncementRecord
import br.com.ajadigital.core.announcements.application.ports.`in`.WithdrawAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementConflict
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementNotFound
import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class WithdrawAnnouncementUseCase(
    private val store: AnnouncementStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : WithdrawAnnouncementInputPort {
    override fun withdraw(request: WithdrawAnnouncementCommand): Announcement = transactionRunner.run {
        val (actor, id, revision) = request
        AnnouncementsPolicy.requireWriter(actor.role, actor.active)
        require(revision > 0)
        store.withdraw(WithdrawAnnouncementRecord(actor.id, actor.role, actor.sessionValidationReference, id, revision))
            ?: store.announcement(GetStoredAnnouncementQuery(actor.id, actor.role, actor.sessionValidationReference, id))?.let { throw AnnouncementConflict(it.revision) }
            ?: throw AnnouncementNotFound()
    }
}
