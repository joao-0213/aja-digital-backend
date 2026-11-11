package br.com.ajadigital.core.adapter.out.security

import br.com.ajadigital.core.ports.out.PasswordHasher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class Argon2PasswordHasher(private val encoder: PasswordEncoder) : PasswordHasher {
    override fun hash(plaintext: String): String = requireNotNull(encoder.encode(plaintext)) { "Password hashing failed" }
    override fun matches(plaintext: String, encoded: String): Boolean = encoder.matches(plaintext, encoded)
}
