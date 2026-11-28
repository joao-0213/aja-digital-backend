package br.com.ajadigital.core.forum.application.usecases

import br.com.ajadigital.core.forum.application.models.ForumTopicPage
import br.com.ajadigital.core.forum.application.models.ListStoredTopicsQuery
import br.com.ajadigital.core.forum.application.models.ListTopicsQuery
import br.com.ajadigital.core.forum.application.ports.`in`.ListTopicsInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class ListTopicsUseCase(
    private val store: TopicStoreOutputPort,
) : ListTopicsInputPort {
    override fun topics(request: ListTopicsQuery): ForumTopicPage {
        val actor = request.actor
        val page = request.page
        val size = request.size
        val sort = request.sort
        val classId = request.classId
        val subjectId = request.subjectId
        val query = request.query
        val unread = request.unread
        val solved = request.solved
        ForumPolicy.readable(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(page > 0 && size in 1..MAX_PAGE_SIZE && sort in setOf("createdAt", "-createdAt"))
        require(classId == null || ForumPolicy.validId(classId))
        require(subjectId == null || ForumPolicy.validId(subjectId))
        val result = store.topics(ListStoredTopicsQuery(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, classId, subjectId, query?.trim()?.takeIf(String::isNotEmpty), unread, solved))
        return result
    }
}

private const val MAX_PAGE_SIZE = 100
