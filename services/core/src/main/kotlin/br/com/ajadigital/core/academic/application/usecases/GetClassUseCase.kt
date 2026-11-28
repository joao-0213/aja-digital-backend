package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.AcademicStoreFindClassDto
import br.com.ajadigital.core.academic.application.models.GetClassDto
import br.com.ajadigital.core.academic.application.ports.`in`.GetClassInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class GetClassUseCase(private val store: AcademicStoreOutputPort) : GetClassInputPort {
    override fun classById(request: GetClassDto): AcademicClassView {
        val (actor, id) = request
        AcademicPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null)
        return store.findClass(AcademicStoreFindClassDto(id, actor.id, actor.role)) ?: throw AcademicNotFound()
    }
}
