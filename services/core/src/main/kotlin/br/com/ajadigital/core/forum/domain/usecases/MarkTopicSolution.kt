package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.ports.`in`.MarkTopicSolutionUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class MarkTopicSolution(
    private val store: TopicStore,
    private val transactionRunner: TransactionRunner,
) : MarkTopicSolutionUseCase {
    override fun solution(actor: AcademicActor, id: String, revision: Int, replyId: String?): Map<String, Any?> = transactionRunner.run {
        ForumSupport.writable(actor)
        require(revision > 0 && (replyId == null || ForumSupport.validId(replyId)))
        val topicId = ForumSupport.checkedId(id)
        store.solution(actor.id, actor.role, actor.sessionTokenHash, topicId, revision, replyId)
            ?: store.topic(actor.id, actor.role, actor.sessionTokenHash, topicId)?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    }
}
