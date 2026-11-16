package br.com.ajadigital.core.identity.domain.ports.out

import br.com.ajadigital.core.identity.domain.models.User


data class AuthTokenResult(val user: User? = null, val failure: TokenFailure? = null)
