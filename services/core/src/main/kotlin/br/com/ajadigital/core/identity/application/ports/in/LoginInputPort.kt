package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.models.AuthenticatedSession
import br.com.ajadigital.core.identity.application.models.LoginCommand

interface LoginInputPort {
    fun login(request: LoginCommand): AuthenticatedSession
}
