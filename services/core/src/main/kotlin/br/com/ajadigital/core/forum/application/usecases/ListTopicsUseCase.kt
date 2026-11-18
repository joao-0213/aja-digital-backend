package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.ListTopicsInputPort
import br.com.ajadigital.core.forum.domain.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.ListTopicsDto
import br.com.ajadigital.core.forum.domain.models.ListStoredTopicsDto

class ListTopicsUseCase(
    private val store: TopicStoreOutputPort,
) : ListTopicsInputPort {
    override fun topics(request: ListTopicsDto): Map<String, Any?> {
        val (actor, page, size, sort, classId, subjectId, query, unread, solved) = request
                ForumPolicy.readable(actor)
        require(page > 0 && size in 1..100 && sort in setOf("createdAt", "-createdAt"))
        require(classId == null || ForumPolicy.validId(classId))
        require(subjectId == null || ForumPolicy.validId(subjectId))
        val result = store.topics(ListStoredTopicsDto(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, classId, subjectId, query?.trim()?.takeIf(String::isNotEmpty), unread, solved))
        return mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }
}
