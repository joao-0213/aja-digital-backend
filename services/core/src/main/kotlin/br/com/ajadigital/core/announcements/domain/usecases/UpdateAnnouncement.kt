package br.com.ajadigital.core.announcements.domain.usecases

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.ports.`in`.UpdateAnnouncementUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class UpdateAnnouncement(
    private val store: AnnouncementStore,
    private val transactionRunner: TransactionRunner,
) : UpdateAnnouncementUseCase {
    override fun update(actor: AcademicActor, id: String, revision: Int, draft: AnnouncementDraft): Map<String, Any?> = transactionRunner.run {
        AnnouncementsSupport.requireWriter(actor)
        require(revision > 0)
        AnnouncementsSupport.validate(draft)
        store.update(actor.id, actor.role, actor.sessionTokenHash, id, revision, draft)
            ?: store.announcement(actor.id, actor.role, actor.sessionTokenHash, id)?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    }
}
