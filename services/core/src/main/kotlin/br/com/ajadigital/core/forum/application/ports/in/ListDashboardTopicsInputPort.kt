package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic
import br.com.ajadigital.core.forum.application.contracts.ListDashboardTopicsQuery

interface ListDashboardTopicsInputPort {
    fun dashboardTopics(request: ListDashboardTopicsQuery): List<ForumDashboardTopic>
}
