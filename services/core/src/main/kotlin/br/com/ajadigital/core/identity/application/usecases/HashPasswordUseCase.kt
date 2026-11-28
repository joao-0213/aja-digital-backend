package br.com.ajadigital.core.identity.application.usecases

import br.com.ajadigital.core.identity.application.models.HashPasswordDto
import br.com.ajadigital.core.identity.application.ports.`in`.HashPasswordInputPort
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort

class HashPasswordUseCase(private val passwordHasher: PasswordHasherOutputPort) : HashPasswordInputPort {
    override fun hash(password: String): String = passwordHasher.hash(HashPasswordDto(password))
}
