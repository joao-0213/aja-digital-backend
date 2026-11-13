package br.com.ajadigital.core.ports.out

import br.com.ajadigital.core.domain.identity.User


data class AuthTokenResult(val user: User? = null, val failure: TokenFailure? = null)
