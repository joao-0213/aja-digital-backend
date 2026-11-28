package br.com.ajadigital.core.forum.application.usecases

import br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic
import br.com.ajadigital.core.forum.application.contracts.ListDashboardTopicsQuery
import br.com.ajadigital.core.forum.application.models.ListDashboardStoredTopicsQuery
import br.com.ajadigital.core.forum.application.ports.`in`.ListDashboardTopicsInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class ListDashboardTopicsUseCase(
    private val store: TopicStoreOutputPort,
) : ListDashboardTopicsInputPort {
    override fun dashboardTopics(request: ListDashboardTopicsQuery): List<ForumDashboardTopic> = request.let {
        val (actor) = request
        ForumPolicy.readable(actor.role, actor.active, actor.emailConfirmedAt != null).let { store.dashboardTopics(ListDashboardStoredTopicsQuery(actor.id, actor.role, actor.sessionValidationReference, DASHBOARD_TOPIC_LIMIT)) }
    }
}

private const val DASHBOARD_TOPIC_LIMIT = 5
