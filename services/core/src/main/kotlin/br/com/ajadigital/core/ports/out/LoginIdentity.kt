package br.com.ajadigital.core.ports.out

import br.com.ajadigital.core.domain.identity.User


data class LoginIdentity(val user: User, val passwordHash: String)
