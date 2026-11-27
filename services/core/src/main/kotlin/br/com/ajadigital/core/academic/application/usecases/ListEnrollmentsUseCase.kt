package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.AcademicStoreListEnrollmentsDto

import br.com.ajadigital.core.academic.application.models.ListEnrollmentsDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.ListEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort

import br.com.ajadigital.core.shared.application.contracts.Page

class ListEnrollmentsUseCase(private val store: AcademicStoreOutputPort) : ListEnrollmentsInputPort {
    override fun enrollments(request: ListEnrollmentsDto): Page<AcademicEnrollmentView> {
        val (actor, page, size, sort, classId, studentId, status, q) = request
                AcademicPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null)
        return store.listEnrollments(AcademicStoreListEnrollmentsDto(
            actor.id,
            actor.role,
            AcademicPolicy.checkedPage(page),
            AcademicPolicy.checkedSize(size),
            AcademicPolicy.checkedSort(sort),
            classId,
            studentId,
            status?.also { require(it.isNotEmpty() && it.size <= 4 && it.distinct().size == it.size && it.all { s -> s in AcademicPolicy.statuses }) },
            q?.trim(),
        ))
    }
}
