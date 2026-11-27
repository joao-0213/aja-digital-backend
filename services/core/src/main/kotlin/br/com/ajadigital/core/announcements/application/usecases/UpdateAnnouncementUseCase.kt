package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.announcements.application.contracts.Announcement

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.application.models.UpdateAnnouncementCommand
import br.com.ajadigital.core.announcements.application.models.UpdateAnnouncementRecord
import br.com.ajadigital.core.announcements.application.models.GetStoredAnnouncementQuery
import br.com.ajadigital.core.announcements.application.ports.`in`.UpdateAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class UpdateAnnouncementUseCase(
    private val store: AnnouncementStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateAnnouncementInputPort {
    override fun update(request: UpdateAnnouncementCommand): Announcement = transactionRunner.run(TransactionRunDto {
        val (actor, id, revision, draft) = request
                AnnouncementsPolicy.requireWriter(actor)
        require(revision > 0)
        AnnouncementsPolicy.validate(draft)
        store.update(UpdateAnnouncementRecord(actor.id, actor.role, actor.sessionValidationReference, id, revision, draft))
            ?: store.announcement(GetStoredAnnouncementQuery(actor.id, actor.role, actor.sessionValidationReference, id))?.let { throw AcademicConflict(it.revision) }
            ?: throw AcademicNotFound()
    })
}
