package br.com.ajadigital.core.announcements.domain.usecases

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.ports.`in`.CreateAnnouncementUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class CreateAnnouncement(
    private val store: AnnouncementStore,
    private val mapper: ObjectMapper,
    private val idempotency: IdempotencyStore,
    private val transactionRunner: TransactionRunner,
) : CreateAnnouncementUseCase {
    override fun create(actor: AcademicActor, draft: AnnouncementDraft, key: String): Map<String, Any?> = transactionRunner.run {
        AnnouncementsSupport.requireWriter(actor)
        AnnouncementsSupport.validate(draft)
        require(key.length in 8..128)
        store.authorizeCreate(actor.id, actor.role, actor.sessionTokenHash, draft)
        val hash = AnnouncementsSupport.sha256(mapper.writeValueAsBytes(draft))
        try {
            idempotency.find(actor.id, "createAnnouncement", "/announcements", key, hash)?.let { return@run it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        store.create(actor.id, actor.role, actor.sessionTokenHash, draft, key, hash).also {
            idempotency.save(actor.id, "createAnnouncement", "/announcements", key, hash, it)
        }
    }
}
