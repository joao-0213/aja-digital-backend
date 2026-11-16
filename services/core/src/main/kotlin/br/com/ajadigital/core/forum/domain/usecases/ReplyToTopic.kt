package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.ports.`in`.ReplyToTopicUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class ReplyToTopic(
    private val store: TopicStore,
    private val idempotency: IdempotencyStore,
    private val mapper: ObjectMapper,
    private val transactionRunner: TransactionRunner,
) : ReplyToTopicUseCase {
    override fun reply(actor: AcademicActor, id: String, body: String, key: String): Map<String, Any?> = transactionRunner.run {
        ForumSupport.writable(actor)
        require(body.isNotBlank() && body.length <= 10000 && key.length in 8..128)
        val topicId = ForumSupport.checkedId(id)
        store.authorizeReply(actor.id, actor.role, actor.sessionTokenHash, topicId)
        val hash = ForumSupport.sha256(mapper.writeValueAsBytes(mapOf("body" to body)))
        try {
            idempotency.find(actor.id, "replyToTopic", "/topics/$topicId", key, hash)?.let { return@run it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        (store.reply(actor.id, actor.role, actor.sessionTokenHash, topicId, body) ?: throw AcademicNotFound())
            .also { idempotency.save(actor.id, "replyToTopic", "/topics/$topicId", key, hash, it) }
    }
}
