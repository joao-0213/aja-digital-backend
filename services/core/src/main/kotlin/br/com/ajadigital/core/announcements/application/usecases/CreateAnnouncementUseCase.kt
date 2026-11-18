package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementDto
import br.com.ajadigital.core.announcements.domain.models.AuthorizeAnnouncementCreateDto
import br.com.ajadigital.core.announcements.domain.models.CreateAnnouncementRecordDto
import br.com.ajadigital.core.announcements.application.ports.`in`.CreateAnnouncementInputPort
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class CreateAnnouncementUseCase(
    private val store: AnnouncementStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateAnnouncementInputPort {
    override fun create(request: CreateAnnouncementDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, draft, key) = request
                AnnouncementsPolicy.requireWriter(actor)
        AnnouncementsPolicy.validate(draft)
        require(key.length in 8..128)
        store.authorizeCreate(AuthorizeAnnouncementCreateDto(actor.id, actor.role, actor.sessionValidationReference, draft))
        val hash = AnnouncementsPolicy.sha256(mapper.serialize(SerializeJsonDto(draft)))
        try {
            idempotency.find(FindIdempotencyDto(actor.id, "createAnnouncement", "/announcements", key, hash))?.let { return@TransactionRunDto it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        store.create(CreateAnnouncementRecordDto(actor.id, actor.role, actor.sessionValidationReference, draft, key, hash)).also {
            idempotency.save(SaveIdempotencyDto(actor.id, "createAnnouncement", "/announcements", key, hash, it))
        }
    })
}
