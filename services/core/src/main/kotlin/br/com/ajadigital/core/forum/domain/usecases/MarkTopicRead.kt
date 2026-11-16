package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.ports.`in`.MarkTopicReadUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class MarkTopicRead(
    private val store: TopicStore,
    private val transactionRunner: TransactionRunner,
) : MarkTopicReadUseCase {
    override fun read(actor: AcademicActor, id: String): Map<String, Any?> = transactionRunner.run {
        ForumSupport.readable(actor).let { store.read(actor.id, actor.role, actor.sessionTokenHash, ForumSupport.checkedId(id)) ?: throw AcademicNotFound() }
    }
}
