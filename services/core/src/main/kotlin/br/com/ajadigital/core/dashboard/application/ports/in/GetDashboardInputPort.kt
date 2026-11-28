package br.com.ajadigital.core.dashboard.application.ports.`in`
import br.com.ajadigital.core.dashboard.application.models.DashboardRead
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface GetDashboardInputPort {
    fun dashboard(actor: AuthenticatedActor): br.com.ajadigital.core.dashboard.application.models.DashboardRead
}
