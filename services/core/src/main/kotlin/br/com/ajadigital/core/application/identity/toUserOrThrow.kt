package br.com.ajadigital.core.application.identity

import br.com.ajadigital.core.domain.identity.User
import br.com.ajadigital.core.ports.out.AuthTokenResult
import br.com.ajadigital.core.ports.out.TokenFailure


internal fun AuthTokenResult.toUserOrThrow(): User = when {
    user != null -> user
    failure == TokenFailure.EXPIRED -> throw AuthTokenExpired()
    else -> throw AuthTokenInvalid()
}
