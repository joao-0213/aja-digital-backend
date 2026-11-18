package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.UpdateUserDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface UpdateUserInputPort {
    fun updateUser(request: UpdateUserDto): Map<String, Any?>
}
