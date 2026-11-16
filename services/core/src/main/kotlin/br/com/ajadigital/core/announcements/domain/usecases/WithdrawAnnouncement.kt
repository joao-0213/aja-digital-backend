package br.com.ajadigital.core.announcements.domain.usecases

import br.com.ajadigital.core.announcements.domain.ports.`in`.WithdrawAnnouncementUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class WithdrawAnnouncement(
    private val store: AnnouncementStore,
    private val transactionRunner: TransactionRunner,
) : WithdrawAnnouncementUseCase {
    override fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?> = transactionRunner.run {
        AnnouncementsSupport.requireWriter(actor)
        require(revision > 0)
        store.withdraw(actor.id, actor.role, actor.sessionTokenHash, id, revision)
            ?: store.announcement(actor.id, actor.role, actor.sessionTokenHash, id)?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    }
}
