package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.WithdrawUserDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface WithdrawUserInputPort {
    fun withdraw(request: WithdrawUserDto): Map<String, Any?>
}
