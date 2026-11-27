package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.AcademicStoreMyEnrollmentsDto

import br.com.ajadigital.core.academic.application.models.ListMyEnrollmentsDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.ListMyEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort

import br.com.ajadigital.core.shared.application.contracts.Page

class ListMyEnrollmentsUseCase(private val store: AcademicStoreOutputPort) : ListMyEnrollmentsInputPort {
    override fun myEnrollments(request: ListMyEnrollmentsDto): Page<AcademicEnrollmentView> {
                val (actor, page, size, sort) = request
        if (!actor.active || actor.emailConfirmedAt == null) throw AcademicForbidden()
        return store.myEnrollments(AcademicStoreMyEnrollmentsDto(actor.id, AcademicPolicy.checkedPage(page), AcademicPolicy.checkedSize(size), AcademicPolicy.checkedSort(sort)))
    }
}
