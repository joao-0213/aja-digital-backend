package br.com.ajadigital.core.identity.domain.ports.out

import br.com.ajadigital.core.identity.domain.models.User


data class LoginIdentity(val user: User, val passwordHash: String)
