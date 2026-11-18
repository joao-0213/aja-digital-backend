package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ContentStoreDashboardDto

import br.com.ajadigital.core.materials.application.models.GetDashboardContentDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.models.DashboardContent
import br.com.ajadigital.core.materials.application.ports.`in`.GetDashboardContentInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class GetDashboardContentUseCase(private val store: ContentStoreOutputPort) : GetDashboardContentInputPort {
    override fun dashboard(request: GetDashboardContentDto): DashboardContent {
                val (actor) = request
        MaterialsPolicy.reader(actor)
        require(actor.role == "student") { throw AcademicForbidden() }
        return store.dashboard(ContentStoreDashboardDto(actor.id, actor.role, actor.sessionValidationReference, 100))
    }
}
