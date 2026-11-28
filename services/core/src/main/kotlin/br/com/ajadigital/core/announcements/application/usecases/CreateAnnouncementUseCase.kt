package br.com.ajadigital.core.announcements.application.usecases
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.models.AuthorizeAnnouncementCreate
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementCommand
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementRecord
import br.com.ajadigital.core.announcements.application.ports.`in`.CreateAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementReceiptCodecOutputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementIdempotencyConflict
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.policies.AnnouncementsPolicy
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto

class CreateAnnouncementUseCase(
    private val store: AnnouncementStoreOutputPort,
    private val fingerprints: br.com.ajadigital.core.announcements.application.ports.out.AnnouncementRequestFingerprintOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AnnouncementReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateAnnouncementInputPort {
    override fun create(request: CreateAnnouncementCommand): Announcement = transactionRunner.run {
        val (actor, draft, key) = request
        AnnouncementsPolicy.requireWriter(actor.role, actor.active)
        AnnouncementsPolicy.validate(draft)
        require(key.length in MIN_IDEMPOTENCY_KEY_LENGTH..MAX_IDEMPOTENCY_KEY_LENGTH)
        store.authorizeCreate(AuthorizeAnnouncementCreate(actor.id, actor.role, actor.sessionValidationReference, draft))
        val hash = fingerprints.create(draft)
        val receiptKey = FindIdempotencyDto(actor.id, "createAnnouncement", "/announcements", key, hash)
        try {
            idempotency.findEncoded(receiptKey)?.let { return@run receiptCodec.decode(it) }
        } catch (_: IdempotencyKeyReuse) {
            throw AnnouncementIdempotencyConflict()
        }
        store.create(CreateAnnouncementRecord(actor.id, actor.role, actor.sessionValidationReference, draft, key, hash)).also {
            idempotency.saveEncoded(receiptKey, receiptCodec.encode(it))
        }
    }
}

private const val MIN_IDEMPOTENCY_KEY_LENGTH = 8
private const val MAX_IDEMPOTENCY_KEY_LENGTH = 128
