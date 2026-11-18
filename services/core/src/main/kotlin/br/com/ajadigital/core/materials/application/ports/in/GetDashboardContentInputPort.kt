package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.GetDashboardContentDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.models.DashboardContent

interface GetDashboardContentInputPort {
    fun dashboard(request: GetDashboardContentDto): DashboardContent
}
