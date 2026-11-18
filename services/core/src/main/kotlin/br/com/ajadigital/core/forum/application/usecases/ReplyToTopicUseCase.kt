package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.ReplyToTopicInputPort
import br.com.ajadigital.core.forum.domain.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.ReplyToTopicDto
import br.com.ajadigital.core.forum.domain.models.AuthorizeTopicReplyDto
import br.com.ajadigital.core.forum.domain.models.CreateTopicReplyRecordDto
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class ReplyToTopicUseCase(
    private val store: TopicStoreOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : ReplyToTopicInputPort {
    override fun reply(request: ReplyToTopicDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, id, body, key) = request
                ForumPolicy.writable(actor)
        require(body.isNotBlank() && body.length <= 10000 && key.length in 8..128)
        val topicId = ForumPolicy.checkedId(id)
        store.authorizeReply(AuthorizeTopicReplyDto(actor.id, actor.role, actor.sessionValidationReference, topicId))
        val hash = ForumPolicy.sha256(mapper.serialize(SerializeJsonDto(mapOf("body" to body))))
        try {
            idempotency.find(FindIdempotencyDto(actor.id, "replyToTopic", "/topics/$topicId", key, hash))?.let { return@TransactionRunDto it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        (store.reply(CreateTopicReplyRecordDto(actor.id, actor.role, actor.sessionValidationReference, topicId, body)) ?: throw AcademicNotFound())
            .also { idempotency.save(SaveIdempotencyDto(actor.id, "replyToTopic", "/topics/$topicId", key, hash, it)) }
    })
}
