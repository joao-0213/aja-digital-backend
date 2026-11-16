package br.com.ajadigital.core.identity.domain.usecases
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenExpired
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenInvalid

import br.com.ajadigital.core.identity.domain.models.User
import br.com.ajadigital.core.identity.domain.ports.out.AuthTokenResult
import br.com.ajadigital.core.identity.domain.ports.out.TokenFailure


internal fun AuthTokenResult.toUserOrThrow(): User = when {
    user != null -> user
    failure == TokenFailure.EXPIRED -> throw AuthTokenExpired()
    else -> throw AuthTokenInvalid()
}
