package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.announcements.application.contracts.Announcement

import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementCommand
import br.com.ajadigital.core.announcements.application.models.AuthorizeAnnouncementCreate
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementRecord
import br.com.ajadigital.core.announcements.application.ports.`in`.CreateAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementReceiptCodecOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class CreateAnnouncementUseCase(
    private val store: AnnouncementStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AnnouncementReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateAnnouncementInputPort {
    override fun create(request: CreateAnnouncementCommand): Announcement = transactionRunner.run(TransactionRunDto {
        val (actor, draft, key) = request
                AnnouncementsPolicy.requireWriter(actor)
        AnnouncementsPolicy.validate(draft)
        require(key.length in 8..128)
        store.authorizeCreate(AuthorizeAnnouncementCreate(actor.id, actor.role, actor.sessionValidationReference, draft))
        val hash = AnnouncementsPolicy.sha256(mapper.serialize(SerializeJsonDto(draft)))
        val receiptKey = FindIdempotencyDto(actor.id, "createAnnouncement", "/announcements", key, hash)
        try {
            idempotency.findEncoded(receiptKey)?.let { return@TransactionRunDto receiptCodec.decode(it) }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        store.create(CreateAnnouncementRecord(actor.id, actor.role, actor.sessionValidationReference, draft, key, hash)).also {
            idempotency.saveEncoded(receiptKey, receiptCodec.encode(it))
        }
    })
}
