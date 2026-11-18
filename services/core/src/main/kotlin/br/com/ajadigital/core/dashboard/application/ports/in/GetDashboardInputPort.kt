package br.com.ajadigital.core.dashboard.application.ports.`in`
import br.com.ajadigital.core.dashboard.application.models.*

import br.com.ajadigital.core.dashboard.application.models.GetDashboardDto

interface GetDashboardInputPort {
    fun dashboard(request: GetDashboardDto): Map<String, Any?>
}
