package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.DeactivateUserDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface DeactivateUserInputPort {
    fun deactivate(request: DeactivateUserDto): AcademicUserView
}
