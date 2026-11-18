package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.contracts.DashboardContent
import br.com.ajadigital.core.materials.application.ports.`in`.GetDashboardContentInputPort
import br.com.ajadigital.core.materials.application.ports.out.LearningProgressOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsForbidden

class GetDashboardContentUseCase(private val store: LearningProgressOutputPort) : GetDashboardContentInputPort {
    override fun dashboard(actor: AuthenticatedActor): DashboardContent {
        actor.requireReader()
        if (actor.role != "student") throw MaterialsForbidden()
        return store.dashboard(actor, 100)
    }
}
