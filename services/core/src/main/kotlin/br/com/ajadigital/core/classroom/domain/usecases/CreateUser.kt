package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.classroom.domain.ports.out.ProvisionedIdentity
import br.com.ajadigital.core.outbox.domain.ports.out.OutboxPayloadProtector
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.identity.domain.ports.out.PasswordHasher
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

class CreateUser(
    private val store: AcademicStore,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val encryption: OutboxPayloadProtector,
    private val passwordHasher: PasswordHasher,
    private val idempotency: IdempotencyStore,
    private val transactionRunner: TransactionRunner,
) : CreateUserUseCase {
    override fun createUser(actor: AcademicActor, name: String, emailInput: String, role: String, key: String): Map<String, Any?> =
        transactionRunner.run {
            ClassroomSupport.requireAdmin(store, actor, true)
            val trimmedName = name.trim()
            val email = emailInput.trim()
            require(trimmedName.isNotEmpty() && trimmedName.length <= 200 && EmailAddress.isValid(email) && role in setOf("teacher", "admin"))
            ClassroomSupport.idempotent(idempotency, objectMapper, actor, "createUser", "/users", key, mapOf("name" to trimmedName, "email" to email, "role" to role)) {
                val now = Instant.now(clock)
                val id = UUID.randomUUID().toString()
                val passwordToken = ClassroomSupport.token()
                val confirmationToken = ClassroomSupport.token()
                val passwordEvent = UUID.randomUUID().toString()
                val confirmationEvent = UUID.randomUUID().toString()
                val passwordPayload = objectMapper.writeValueAsBytes(mapOf("recipient" to email, "token" to passwordToken, "userId" to id, "purpose" to "password_reset"))
                val confirmationPayload = objectMapper.writeValueAsBytes(mapOf("recipient" to email, "token" to confirmationToken, "userId" to id, "purpose" to "email_confirmation"))
                val passwordEncrypted = encryption.encrypt(passwordEvent, "identity.password-reset-requested", 1, passwordPayload)
                val confirmationEncrypted = encryption.encrypt(confirmationEvent, "identity.email-confirmation-requested", 1, confirmationPayload)
                store.createUser(
                    ProvisionedIdentity(
                        id,
                        actor.id,
                        trimmedName,
                        email,
                        role,
                        now,
                        passwordHasher.hash(ClassroomSupport.token()),
                        UUID.randomUUID().toString(),
                        ClassroomSupport.sha256(passwordToken),
                        passwordEvent,
                        passwordEncrypted.ciphertext,
                        passwordEncrypted.nonce,
                        UUID.randomUUID().toString(),
                        ClassroomSupport.sha256(confirmationToken),
                        confirmationEvent,
                        confirmationEncrypted.ciphertext,
                        confirmationEncrypted.nonce,
                        encryption.keyId,
                    ),
                )
            }
        }
}
