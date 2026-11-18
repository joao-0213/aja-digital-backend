package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.AcademicStoreFindClassDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.models.GetClassDto
import br.com.ajadigital.core.academic.application.ports.`in`.GetClassInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort

class GetClassUseCase(private val store: AcademicStoreOutputPort) : GetClassInputPort {
    override fun classById(request: GetClassDto): Map<String, Any?> {
        val (actor, id) = request
                AcademicPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null)
        return store.findClass(AcademicStoreFindClassDto(id, actor.id, actor.role)) ?: throw AcademicNotFound()
    }
}
