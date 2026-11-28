package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.AcademicStoreFindStudentDto
import br.com.ajadigital.core.academic.application.models.AcademicStudentView
import br.com.ajadigital.core.academic.application.models.GetStudentDto
import br.com.ajadigital.core.academic.application.ports.`in`.GetStudentInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class GetStudentUseCase(private val store: AcademicStoreOutputPort) : GetStudentInputPort {
    override fun student(request: GetStudentDto): AcademicStudentView {
        val (actor, id) = request
        AcademicPolicy.requireStaffReader(actor.role, actor.active)
        return store.findStudent(AcademicStoreFindStudentDto(id, actor.id, actor.role)) ?: throw AcademicNotFound()
    }
}
