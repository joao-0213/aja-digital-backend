package br.com.ajadigital.core.identity.domain.ports.`in`

import br.com.ajadigital.core.identity.domain.models.AcceptedResponse
import br.com.ajadigital.core.identity.domain.models.User

interface RequestAuthTokenUseCase {
    fun request(emailInput: String, purpose: String): AcceptedResponse
    fun confirmEmail(token: String): User
}
