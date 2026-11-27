package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization

import br.com.ajadigital.core.academic.application.models.FindUserDto
import br.com.ajadigital.core.academic.application.models.GetUserDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.GetUserInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort

class GetUserUseCase(private val store: AcademicStoreOutputPort) : GetUserInputPort {
    override fun user(request: GetUserDto): AcademicUserView {
        val (actor, id) = request
                AcademicAuthorization.requireAdmin(store, actor)
        return store.findUser(FindUserDto(id)) ?: throw AcademicNotFound()
    }
}
