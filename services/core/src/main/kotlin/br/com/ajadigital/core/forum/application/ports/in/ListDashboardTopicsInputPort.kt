package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.ListDashboardTopicsDto

interface ListDashboardTopicsInputPort {
    fun dashboardTopics(request: ListDashboardTopicsDto): List<Map<String, Any?>>
}
