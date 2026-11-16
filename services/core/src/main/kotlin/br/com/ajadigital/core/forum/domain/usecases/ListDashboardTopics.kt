package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.ports.`in`.ListDashboardTopicsUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore

class ListDashboardTopics(
    private val store: TopicStore,
) : ListDashboardTopicsUseCase {
    override fun dashboardTopics(actor: AcademicActor): List<Map<String, Any?>> =
        ForumSupport.readable(actor).let { store.dashboardTopics(actor.id, actor.role, actor.sessionTokenHash, 5) }
}
