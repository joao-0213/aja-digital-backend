package br.com.ajadigital.core.identity.application.models

import br.com.ajadigital.core.identity.domain.models.User

data class AuthTokenResult(
    val user: User? = null,
    val failure: TokenFailure? = null,
)
