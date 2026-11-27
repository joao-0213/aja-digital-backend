package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization

import br.com.ajadigital.core.academic.application.models.AcademicStoreListUsersDto

import br.com.ajadigital.core.academic.application.models.ListUsersDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.ListUsersInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort

import br.com.ajadigital.core.shared.application.contracts.Page

class ListUsersUseCase(private val store: AcademicStoreOutputPort) : ListUsersInputPort {
    override fun users(request: ListUsersDto): Page<AcademicUserView> {
        val (actor, page, size, sort, role, active, q) = request
                AcademicAuthorization.requireAdmin(store, actor)
        return store.listUsers(AcademicStoreListUsersDto(
            AcademicPolicy.checkedPage(page),
            AcademicPolicy.checkedSize(size),
            AcademicPolicy.checkedSort(sort),
            role?.also { require(it in AcademicPolicy.roles) },
            active,
            q?.trim(),
        ))
    }
}
