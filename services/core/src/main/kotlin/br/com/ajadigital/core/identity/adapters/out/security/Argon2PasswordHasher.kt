package br.com.ajadigital.core.identity.adapters.out.security

import br.com.ajadigital.core.identity.application.models.HashPasswordDto
import br.com.ajadigital.core.identity.application.models.VerifyPasswordDto
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class Argon2PasswordHasher(
    private val encoder: PasswordEncoder,
) : PasswordHasherOutputPort {
    override fun hash(request: HashPasswordDto): String = requireNotNull(encoder.encode(request.plaintext)) { "Password hashing failed" }

    override fun matches(request: VerifyPasswordDto): Boolean = encoder.matches(request.plaintext, request.encoded)
}
