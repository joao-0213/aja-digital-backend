package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy

import br.com.ajadigital.core.forum.domain.exceptions.ForumIdempotencyConflict
import br.com.ajadigital.core.forum.domain.exceptions.ForumNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.ReplyToTopicInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.ReplyToTopicCommand
import br.com.ajadigital.core.forum.application.models.AuthorizeTopicReplyCommand
import br.com.ajadigital.core.forum.application.models.CreateTopicReplyRecord
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.forum.application.ports.out.ForumRequestFingerprintOutputPort

class ReplyToTopicUseCase(
    private val store: TopicStoreOutputPort,
    private val fingerprints: ForumRequestFingerprintOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : ReplyToTopicInputPort {
    override fun reply(request: ReplyToTopicCommand): ForumTopicView = transactionRunner.run {
        val (actor, id, body, key) = request
                ForumPolicy.writable(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(body.isNotBlank() && body.length <= 10000 && key.length in 8..128)
        val topicId = ForumPolicy.checkedId(id)
        store.authorizeReply(AuthorizeTopicReplyCommand(actor.id, actor.role, actor.sessionValidationReference, topicId))
        val hash = fingerprints.reply(body)
        store.findReplyReceipt(FindForumReplyReceiptQuery(actor.id, topicId, key, hash))?.let { return@run it }
        (store.reply(CreateTopicReplyRecord(actor.id, actor.role, actor.sessionValidationReference, topicId, body)) ?: throw ForumNotFound())
            .also { store.saveReplyReceipt(SaveForumReplyReceiptCommand(actor.id, topicId, key, hash, it)) }
    }
}
