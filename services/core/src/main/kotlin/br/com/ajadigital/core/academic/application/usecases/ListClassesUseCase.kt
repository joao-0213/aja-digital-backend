package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.AcademicStoreListClassesDto
import br.com.ajadigital.core.academic.application.models.ListClassesDto
import br.com.ajadigital.core.academic.application.ports.`in`.ListClassesInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.Page

class ListClassesUseCase(private val store: AcademicStoreOutputPort) : ListClassesInputPort {
    override fun classes(request: ListClassesDto): Page<AcademicClassView> {
        val actor = request.actor
        val page = request.page
        val size = request.size
        val sort = request.sort
        val active = request.active
        val q = request.q
        AcademicPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null)
        return store.listClasses(AcademicStoreListClassesDto(actor.id, actor.role, AcademicPolicy.checkedPage(page), AcademicPolicy.checkedSize(size), AcademicPolicy.checkedSort(sort), active, q?.trim()))
    }
}
