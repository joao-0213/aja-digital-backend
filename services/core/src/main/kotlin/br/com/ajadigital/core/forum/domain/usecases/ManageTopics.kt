package br.com.ajadigital.core.forum.domain.usecases
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.forum.domain.exceptions.InvalidTopicReference

import br.com.ajadigital.core.forum.domain.ports.`in`.ManageTopicsUseCase
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest


class ManageTopics(
    private val store: TopicStore,
    private val questions: QuestionSnapshotProvider,
    private val idempotency: IdempotencyStore,
    private val mapper: ObjectMapper,
    private val transactionRunner: TransactionRunner,
) : ManageTopicsUseCase {
    override fun create(actor: AcademicActor, draft: TopicDraft, key: String): Map<String, Any?> {
        writable(actor)
        require(key.length in 8..128 && validId(draft.classId) && validId(draft.subjectId))
        require(draft.title.isNotBlank() && draft.title.length <= 160 && draft.body.isNotBlank() && draft.body.length <= 10000)
        require(draft.tags.size <= 20 && draft.tags.all { it.isNotBlank() && it.length <= 40 } && draft.tags.distinct().size == draft.tags.size)
        require((draft.targetType == null) == (draft.targetId == null))
        require(draft.targetType == null || draft.targetType in setOf("question", "material") && validId(draft.targetId!!))
        require(draft.attachmentId == null || validId(draft.attachmentId))
        val hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(draft)).joinToString("") { "%02x".format(it) }
        store.authorizeAndFindReplay(actor.id, actor.role, actor.sessionTokenHash, draft, key, hash)?.let { return it }
        if (draft.targetType == "question") {
            val snapshots = questions.snapshots(listOf(draft.targetId!!))
            if (snapshots.size != 1 || snapshots.single().questionId != draft.targetId) throw InvalidTopicReference()
        }
        return store.create(actor.id, actor.role, actor.sessionTokenHash, draft, key, hash)
    }

    override fun topics(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?, unread: Boolean?, solved: Boolean?): Map<String, Any?> {
        readable(actor); require(page > 0 && size in 1..100 && sort in setOf("createdAt", "-createdAt"))
        require(classId == null || validId(classId)); require(subjectId == null || validId(subjectId))
        val result = store.topics(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, classId, subjectId, query?.trim()?.takeIf(String::isNotEmpty), unread, solved)
        return mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }

    override fun dashboardTopics(actor: AcademicActor) = readable(actor).let { store.dashboardTopics(actor.id, actor.role, actor.sessionTokenHash, 5) }

    override fun topic(actor: AcademicActor, id: String) = readable(actor).let { store.topic(actor.id, actor.role, actor.sessionTokenHash, checkedId(id)) ?: throw AcademicNotFound() }
    override fun reply(actor: AcademicActor, id: String, body: String, key: String): Map<String, Any?> = transactionRunner.run {
        writable(actor); require(body.isNotBlank() && body.length <= 10000 && key.length in 8..128)
        val topicId = checkedId(id)
        store.authorizeReply(actor.id, actor.role, actor.sessionTokenHash, topicId)
        val hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(mapOf("body" to body))).joinToString("") { "%02x".format(it) }
        try { idempotency.find(actor.id, "replyToTopic", "/topics/$topicId", key, hash)?.let { return@run it } }
        catch (_: IdempotencyKeyReuse) { throw AcademicIdempotencyConflict() }
        (store.reply(actor.id, actor.role, actor.sessionTokenHash, topicId, body) ?: throw AcademicNotFound())
            .also { idempotency.save(actor.id, "replyToTopic", "/topics/$topicId", key, hash, it) }
    }
    override fun read(actor: AcademicActor, id: String) = transactionRunner.run {
        readable(actor).let { store.read(actor.id, actor.role, actor.sessionTokenHash, checkedId(id)) ?: throw AcademicNotFound() }
    }
    override fun solution(actor: AcademicActor, id: String, revision: Int, replyId: String?): Map<String, Any?> = transactionRunner.run {
        writable(actor); require(revision > 0 && (replyId == null || validId(replyId)))
        val topicId = checkedId(id)
        store.solution(actor.id, actor.role, actor.sessionTokenHash, topicId, revision, replyId)
            ?: store.topic(actor.id, actor.role, actor.sessionTokenHash, topicId)?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    }
    override fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?> = transactionRunner.run {
        writable(actor); require(revision > 0)
        val topicId = checkedId(id)
        store.withdraw(actor.id, actor.role, actor.sessionTokenHash, topicId, revision)
            ?: store.topic(actor.id, actor.role, actor.sessionTokenHash, topicId)?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    }
    override fun vote(actor: AcademicActor, id: String, replyId: String, voted: Boolean): Map<String, Any?> = transactionRunner.run {
        writable(actor)
        store.vote(actor.id, actor.role, actor.sessionTokenHash, checkedId(id), checkedId(replyId), voted) ?: throw AcademicNotFound()
    }
    override fun withdrawReply(actor: AcademicActor, id: String, replyId: String, revision: Int): Map<String, Any?> = transactionRunner.run {
        writable(actor); require(revision > 0)
        val topicId = checkedId(id); val checkedReplyId = checkedId(replyId)
        store.withdrawReply(actor.id, actor.role, actor.sessionTokenHash, topicId, checkedReplyId, revision)
            ?: store.topic(actor.id, actor.role, actor.sessionTokenHash, topicId)?.let { topic ->
                if ((topic["replies"] as? List<*>)?.any { (it as? Map<*, *>)?.get("id") == checkedReplyId } == true) throw AcademicConflict()
                throw AcademicNotFound()
            } ?: throw AcademicNotFound()
    }

    private fun readable(actor: AcademicActor) = actor.takeIf { it.active && it.role in setOf("admin", "teacher", "student") && (it.role != "student" || it.emailConfirmedAt != null) } ?: throw AcademicForbidden()
    private fun writable(actor: AcademicActor) = readable(actor)
    private fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))
    private fun checkedId(value: String) = value.also { require(validId(it)) }
}
