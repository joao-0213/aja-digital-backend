package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.CreateUserDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface CreateUserInputPort {
    fun createUser(request: CreateUserDto): AcademicUserView
}
