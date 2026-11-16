package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.ports.`in`.ListTopicsUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore

class ListTopics(
    private val store: TopicStore,
) : ListTopicsUseCase {
    override fun topics(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?, unread: Boolean?, solved: Boolean?): Map<String, Any?> {
        ForumSupport.readable(actor)
        require(page > 0 && size in 1..100 && sort in setOf("createdAt", "-createdAt"))
        require(classId == null || ForumSupport.validId(classId))
        require(subjectId == null || ForumSupport.validId(subjectId))
        val result = store.topics(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, classId, subjectId, query?.trim()?.takeIf(String::isNotEmpty), unread, solved)
        return mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }
}
