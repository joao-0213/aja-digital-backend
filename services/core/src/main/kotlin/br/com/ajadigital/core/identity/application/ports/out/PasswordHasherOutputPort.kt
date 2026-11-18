package br.com.ajadigital.core.identity.application.ports.out

import br.com.ajadigital.core.identity.application.models.HashPasswordDto
import br.com.ajadigital.core.identity.application.models.VerifyPasswordDto

interface PasswordHasherOutputPort {
    fun hash(request: HashPasswordDto): String
    fun matches(request: VerifyPasswordDto): Boolean
}
