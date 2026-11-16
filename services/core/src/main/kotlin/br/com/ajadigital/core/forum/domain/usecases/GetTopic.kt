package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.ports.`in`.GetTopicUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore

class GetTopic(
    private val store: TopicStore,
) : GetTopicUseCase {
    override fun topic(actor: AcademicActor, id: String): Map<String, Any?> =
        ForumSupport.readable(actor).let { store.topic(actor.id, actor.role, actor.sessionTokenHash, ForumSupport.checkedId(id)) ?: throw AcademicNotFound() }
}
