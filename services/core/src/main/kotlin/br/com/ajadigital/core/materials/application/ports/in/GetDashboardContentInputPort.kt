package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.contracts.DashboardContent

fun interface GetDashboardContentInputPort {
    fun dashboard(actor: AuthenticatedActor): DashboardContent
}
