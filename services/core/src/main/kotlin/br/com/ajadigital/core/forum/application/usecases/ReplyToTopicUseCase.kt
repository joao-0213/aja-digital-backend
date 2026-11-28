package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.AuthorizeTopicReplyCommand
import br.com.ajadigital.core.forum.application.models.CreateTopicReplyRecord
import br.com.ajadigital.core.forum.application.models.FindForumReplyReceiptQuery
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.ReplyToTopicCommand
import br.com.ajadigital.core.forum.application.models.SaveForumReplyReceiptCommand
import br.com.ajadigital.core.forum.application.ports.`in`.ReplyToTopicInputPort
import br.com.ajadigital.core.forum.application.ports.out.ForumRequestFingerprintOutputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.domain.exceptions.ForumIdempotencyConflict
import br.com.ajadigital.core.forum.domain.exceptions.ForumNotFound
import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class ReplyToTopicUseCase(
    private val store: TopicStoreOutputPort,
    private val fingerprints: ForumRequestFingerprintOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : ReplyToTopicInputPort {
    override fun reply(request: ReplyToTopicCommand): ForumTopicView = transactionRunner.run {
        val actor = request.actor
        val id = request.id
        val body = request.body
        val key = request.key
        ForumPolicy.writable(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(body.isNotBlank() && body.length <= MAX_TEXT_LENGTH && key.length in MIN_IDEMPOTENCY_KEY_LENGTH..MAX_IDEMPOTENCY_KEY_LENGTH)
        val topicId = ForumPolicy.checkedId(id)
        store.authorizeReply(AuthorizeTopicReplyCommand(actor.id, actor.role, actor.sessionValidationReference, topicId))
        val hash = fingerprints.reply(body)
        store.findReplyReceipt(FindForumReplyReceiptQuery(actor.id, topicId, key, hash))?.let { return@run it }
        (store.reply(CreateTopicReplyRecord(actor.id, actor.role, actor.sessionValidationReference, topicId, body)) ?: throw ForumNotFound())
            .also { store.saveReplyReceipt(SaveForumReplyReceiptCommand(actor.id, topicId, key, hash, it)) }
    }
}

private const val MIN_IDEMPOTENCY_KEY_LENGTH = 8
private const val MAX_IDEMPOTENCY_KEY_LENGTH = 128
private const val MAX_TEXT_LENGTH = 10000
