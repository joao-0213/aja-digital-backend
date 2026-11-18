package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.ListDashboardTopicsInputPort
import br.com.ajadigital.core.forum.domain.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.ListDashboardTopicsDto
import br.com.ajadigital.core.forum.domain.models.ListDashboardStoredTopicsDto

class ListDashboardTopicsUseCase(
    private val store: TopicStoreOutputPort,
) : ListDashboardTopicsInputPort {
    override fun dashboardTopics(request: ListDashboardTopicsDto): List<Map<String, Any?>> = request.let {
        val (actor) = request
        ForumPolicy.readable(actor).let { store.dashboardTopics(ListDashboardStoredTopicsDto(actor.id, actor.role, actor.sessionValidationReference, 5)) }
    }
}
