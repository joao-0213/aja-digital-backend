package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.AcademicStoreListStudentsDto
import br.com.ajadigital.core.academic.application.models.AcademicStudentSummaryView
import br.com.ajadigital.core.academic.application.models.ListStudentsDto
import br.com.ajadigital.core.academic.application.ports.`in`.ListStudentsInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.Page

class ListStudentsUseCase(private val store: AcademicStoreOutputPort) : ListStudentsInputPort {
    override fun students(request: ListStudentsDto): Page<AcademicStudentSummaryView> {
        val actor = request.actor
        val page = request.page
        val size = request.size
        val sort = request.sort
        val classId = request.classId
        val q = request.q
        val history = request.history
        AcademicPolicy.requireStaffReader(actor.role, actor.active)
        return store.listStudents(AcademicStoreListStudentsDto(actor.id, actor.role, AcademicPolicy.checkedPage(page), AcademicPolicy.checkedSize(size), AcademicPolicy.checkedSort(sort), classId, q?.trim(), history))
    }
}
