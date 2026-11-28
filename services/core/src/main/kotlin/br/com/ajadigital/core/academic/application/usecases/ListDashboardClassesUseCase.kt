package br.com.ajadigital.core.academic.application.usecases

import br.com.ajadigital.core.academic.application.models.AcademicStoreDashboardClassesDto
import br.com.ajadigital.core.academic.application.models.ListDashboardClassesDto
import br.com.ajadigital.core.academic.application.ports.`in`.ListDashboardClassesInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class ListDashboardClassesUseCase(private val store: AcademicStoreOutputPort) : ListDashboardClassesInputPort {
    override fun dashboardClasses(request: ListDashboardClassesDto): List<br.com.ajadigital.core.academic.application.contracts.DashboardClassProjection> {
        val (actor, pageSize) = request
        AcademicPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null)
        return store.dashboardClasses(AcademicStoreDashboardClassesDto(actor.id, actor.role, pageSize))
    }
}
