package br.com.ajadigital.core.identity.application.usecases
import br.com.ajadigital.core.identity.application.models.RegistrationRecords
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto
import br.com.ajadigital.core.identity.application.models.RegistrationCommand
import br.com.ajadigital.core.identity.domain.exceptions.RegistrationConflict
import br.com.ajadigital.core.identity.application.models.RegistrationResult
import br.com.ajadigital.core.identity.application.models.RegisterStudentDto
import br.com.ajadigital.core.identity.application.models.CheckEmailExistsDto
import br.com.ajadigital.core.identity.application.models.RecordIdentityAuditDto
import br.com.ajadigital.core.identity.application.models.HashPasswordDto
import br.com.ajadigital.core.identity.application.models.CreateRegistrationDto

import br.com.ajadigital.core.identity.domain.models.User
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.identity.application.ports.`in`.RegisterStudentInputPort
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID


class RegisterStudentUseCase(
    private val store: IdentityStoreOutputPort,
    private val passwordHasher: PasswordHasherOutputPort,
    private val objectMapper: JsonSerializerOutputPort,
    private val clock: Clock,
    private val encryption: OutboxPayloadProtectorOutputPort,
) : RegisterStudentInputPort {
    override fun register(request: RegisterStudentDto): RegistrationResult {
        val (command) = request
        val email = command.email.trim()
        val normalizedEmail = email.lowercase()
        require(
            command.name.isNotBlank() && command.password.length in 8..128 && EmailAddress.isValid(email),
        )
        if (store.emailExists(CheckEmailExistsDto(normalizedEmail))) {
            store.recordAudit(RecordIdentityAuditDto("auth.registration", "conflict"))
            throw RegistrationConflict()
        }

        val now = Instant.now(clock)
        val id = UUID.randomUUID().toString()
        val tokenId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val user = User(id, 1, now, now, id, command.name.trim(), email)
        val expires = now.plus(Duration.ofMinutes(30))
        val payload = objectMapper.serialize(SerializeJsonDto(mapOf("recipient" to email, "token" to token, "userId" to id, "purpose" to "email_confirmation")))
        val encrypted = encryption.encrypt(EncryptOutboxPayloadDto(eventId, "identity.email-confirmation-requested", 1, payload))
        val passwordHash = passwordHasher.hash(HashPasswordDto(command.password))
        store.createRegistration(
            CreateRegistrationDto(br.com.ajadigital.core.identity.application.models.RegistrationRecords(
                user, passwordHash, tokenId, sha256(token), expires,
                eventId, encrypted.ciphertext, encrypted.nonce, encrypted.keyId,
            )),
        )
        return RegistrationResult(user)
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
