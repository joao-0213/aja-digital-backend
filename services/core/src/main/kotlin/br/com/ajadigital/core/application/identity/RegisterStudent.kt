package br.com.ajadigital.core.application.identity

import br.com.ajadigital.core.domain.identity.User
import br.com.ajadigital.core.domain.identity.EmailAddress
import br.com.ajadigital.core.ports.out.IdentityStore
import br.com.ajadigital.core.ports.out.PasswordHasher
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID


class RegisterStudent(
    private val store: IdentityStore,
    private val passwordHasher: PasswordHasher,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val encryption: OutboxEncryption,
) {
    fun register(command: RegistrationCommand): RegistrationResult {
        val email = command.email.trim()
        val normalizedEmail = email.lowercase()
        require(
            command.name.isNotBlank() && command.password.length in 8..128 && EmailAddress.isValid(email),
        )
        if (store.emailExists(normalizedEmail)) {
            store.recordAudit("auth.registration", "conflict")
            throw RegistrationConflict()
        }

        val now = Instant.now(clock)
        val id = UUID.randomUUID().toString()
        val tokenId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val user = User(id, 1, now, now, id, command.name.trim(), email)
        val expires = now.plus(Duration.ofMinutes(30))
        val payload = objectMapper.writeValueAsBytes(mapOf("recipient" to email, "token" to token, "userId" to id, "purpose" to "email_confirmation"))
        val encrypted = encryption.encrypt(eventId, "identity.email-confirmation-requested", 1, payload)
        val passwordHash = passwordHasher.hash(command.password)
        store.createRegistration(
            br.com.ajadigital.core.ports.out.RegistrationRecords(
                user, passwordHash, tokenId, sha256(token), expires,
                eventId, encrypted.ciphertext, encrypted.nonce, encrypted.keyId,
            ),
        )
        return RegistrationResult(user)
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
