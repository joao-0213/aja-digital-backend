package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.ports.`in`.WithdrawTopicReplyUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class WithdrawTopicReply(
    private val store: TopicStore,
    private val transactionRunner: TransactionRunner,
) : WithdrawTopicReplyUseCase {
    override fun withdrawReply(actor: AcademicActor, id: String, replyId: String, revision: Int): Map<String, Any?> = transactionRunner.run {
        ForumSupport.writable(actor)
        require(revision > 0)
        val topicId = ForumSupport.checkedId(id)
        val checkedReplyId = ForumSupport.checkedId(replyId)
        store.withdrawReply(actor.id, actor.role, actor.sessionTokenHash, topicId, checkedReplyId, revision)
            ?: store.topic(actor.id, actor.role, actor.sessionTokenHash, topicId)?.let { topic ->
                if ((topic["replies"] as? List<*>)?.any { (it as? Map<*, *>)?.get("id") == checkedReplyId } == true) throw AcademicConflict()
                throw AcademicNotFound()
            } ?: throw AcademicNotFound()
    }
}
