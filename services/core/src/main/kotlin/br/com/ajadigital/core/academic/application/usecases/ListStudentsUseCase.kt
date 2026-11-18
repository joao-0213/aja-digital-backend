package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.AcademicStoreListStudentsDto

import br.com.ajadigital.core.academic.application.models.ListStudentsDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.ListStudentsInputPort
import br.com.ajadigital.core.academic.application.models.AcademicPage
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort

class ListStudentsUseCase(private val store: AcademicStoreOutputPort) : ListStudentsInputPort {
    override fun students(request: ListStudentsDto): AcademicPage {
        val (actor, page, size, sort, classId, q, history) = request
                AcademicPolicy.requireStaffReader(actor.role, actor.active)
        return store.listStudents(AcademicStoreListStudentsDto(actor.id, actor.role, AcademicPolicy.checkedPage(page), AcademicPolicy.checkedSize(size), AcademicPolicy.checkedSort(sort), classId, q?.trim(), history))
    }
}
