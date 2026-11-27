package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.ReplyToTopicInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.ReplyToTopicCommand
import br.com.ajadigital.core.forum.application.models.AuthorizeTopicReplyCommand
import br.com.ajadigital.core.forum.application.models.CreateTopicReplyRecord
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class ReplyToTopicUseCase(
    private val store: TopicStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : ReplyToTopicInputPort {
    override fun reply(request: ReplyToTopicCommand): ForumTopicView = transactionRunner.run(TransactionRunDto {
        val (actor, id, body, key) = request
                ForumPolicy.writable(actor)
        require(body.isNotBlank() && body.length <= 10000 && key.length in 8..128)
        val topicId = ForumPolicy.checkedId(id)
        store.authorizeReply(AuthorizeTopicReplyCommand(actor.id, actor.role, actor.sessionValidationReference, topicId))
        val hash = ForumPolicy.sha256(mapper.serialize(SerializeJsonDto(mapOf("body" to body))))
        store.findReplyReceipt(FindForumReplyReceiptQuery(actor.id, topicId, key, hash))?.let { return@TransactionRunDto it }
        (store.reply(CreateTopicReplyRecord(actor.id, actor.role, actor.sessionValidationReference, topicId, body)) ?: throw AcademicNotFound())
            .also { store.saveReplyReceipt(SaveForumReplyReceiptCommand(actor.id, topicId, key, hash, it)) }
    })
}
