package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.ListTopicsInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.ListTopicsQuery
import br.com.ajadigital.core.forum.application.models.ForumTopicPage
import br.com.ajadigital.core.forum.application.models.ListStoredTopicsQuery

class ListTopicsUseCase(
    private val store: TopicStoreOutputPort,
) : ListTopicsInputPort {
    override fun topics(request: ListTopicsQuery): ForumTopicPage {
        val (actor, page, size, sort, classId, subjectId, query, unread, solved) = request
                ForumPolicy.readable(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(page > 0 && size in 1..100 && sort in setOf("createdAt", "-createdAt"))
        require(classId == null || ForumPolicy.validId(classId))
        require(subjectId == null || ForumPolicy.validId(subjectId))
        val result = store.topics(ListStoredTopicsQuery(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, classId, subjectId, query?.trim()?.takeIf(String::isNotEmpty), unread, solved))
        return result
    }
}
