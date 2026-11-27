package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.ListUsersDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

import br.com.ajadigital.core.shared.application.contracts.Page

interface ListUsersInputPort {
    fun users(request: ListUsersDto): Page<AcademicUserView>
}
