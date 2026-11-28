package br.com.ajadigital.core.academic.application.ports.`in`

import br.com.ajadigital.core.academic.application.contracts.DashboardClassProjection
import br.com.ajadigital.core.academic.application.models.ListDashboardClassesDto
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface ListDashboardClassesInputPort {
    fun dashboardClasses(request: ListDashboardClassesDto): List<DashboardClassProjection>
}
