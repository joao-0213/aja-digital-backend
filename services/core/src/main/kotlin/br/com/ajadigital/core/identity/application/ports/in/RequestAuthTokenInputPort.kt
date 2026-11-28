package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.models.AcceptedResponse
import br.com.ajadigital.core.identity.application.models.ConfirmEmailTokenDto
import br.com.ajadigital.core.identity.application.models.RequestAuthTokenDto
import br.com.ajadigital.core.identity.domain.models.User

interface RequestAuthTokenInputPort {
    fun request(request: RequestAuthTokenDto): AcceptedResponse
    fun confirmEmail(request: ConfirmEmailTokenDto): User
}
