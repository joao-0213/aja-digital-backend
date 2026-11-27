package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.AcademicStoreListClassesDto

import br.com.ajadigital.core.academic.application.models.ListClassesDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.ListClassesInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort

import br.com.ajadigital.core.shared.application.contracts.Page

class ListClassesUseCase(private val store: AcademicStoreOutputPort) : ListClassesInputPort {
    override fun classes(request: ListClassesDto): Page<AcademicClassView> {
        val (actor, page, size, sort, active, q) = request
                AcademicPolicy.requireReader(actor.role, actor.active, actor.emailConfirmedAt != null)
        return store.listClasses(AcademicStoreListClassesDto(actor.id, actor.role, AcademicPolicy.checkedPage(page), AcademicPolicy.checkedSize(size), AcademicPolicy.checkedSort(sort), active, q?.trim()))
    }
}
