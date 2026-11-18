package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.announcements.application.ports.`in`.WithdrawAnnouncementInputPort
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.models.WithdrawAnnouncementDto
import br.com.ajadigital.core.announcements.domain.models.WithdrawAnnouncementRecordDto
import br.com.ajadigital.core.announcements.domain.models.GetStoredAnnouncementDto
import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class WithdrawAnnouncementUseCase(
    private val store: AnnouncementStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : WithdrawAnnouncementInputPort {
    override fun withdraw(request: WithdrawAnnouncementDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, id, revision) = request
                AnnouncementsPolicy.requireWriter(actor)
        require(revision > 0)
        store.withdraw(WithdrawAnnouncementRecordDto(actor.id, actor.role, actor.sessionValidationReference, id, revision))
            ?: store.announcement(GetStoredAnnouncementDto(actor.id, actor.role, actor.sessionValidationReference, id))?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    })
}
