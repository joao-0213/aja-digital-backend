package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.ports.`in`.VoteTopicReplyUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class VoteTopicReply(
    private val store: TopicStore,
    private val transactionRunner: TransactionRunner,
) : VoteTopicReplyUseCase {
    override fun vote(actor: AcademicActor, id: String, replyId: String, voted: Boolean): Map<String, Any?> = transactionRunner.run {
        ForumSupport.writable(actor)
        store.vote(actor.id, actor.role, actor.sessionTokenHash, ForumSupport.checkedId(id), ForumSupport.checkedId(replyId), voted) ?: throw AcademicNotFound()
    }
}
