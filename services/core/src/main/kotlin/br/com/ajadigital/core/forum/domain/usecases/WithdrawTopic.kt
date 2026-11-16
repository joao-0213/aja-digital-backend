package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.ports.`in`.WithdrawTopicUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class WithdrawTopic(
    private val store: TopicStore,
    private val transactionRunner: TransactionRunner,
) : WithdrawTopicUseCase {
    override fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?> = transactionRunner.run {
        ForumSupport.writable(actor)
        require(revision > 0)
        val topicId = ForumSupport.checkedId(id)
        store.withdraw(actor.id, actor.role, actor.sessionTokenHash, topicId, revision)
            ?: store.topic(actor.id, actor.role, actor.sessionTokenHash, topicId)?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    }
}
