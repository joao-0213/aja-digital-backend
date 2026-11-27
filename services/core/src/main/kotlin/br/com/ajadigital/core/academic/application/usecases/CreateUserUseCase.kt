package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency
import br.com.ajadigital.core.academic.application.capabilities.AcademicTokens

import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateUserDto

import br.com.ajadigital.core.academic.application.models.CreateUserDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.CreateUserInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.application.models.ProvisionedIdentity
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto
import br.com.ajadigital.core.outbox.domain.models.OutboxPayloadKeyDto
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import br.com.ajadigital.core.identity.application.models.HashPasswordDto
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import java.time.Clock
import java.time.Instant
import java.util.UUID

class CreateUserUseCase(
    private val store: AcademicStoreOutputPort,
    private val objectMapper: JsonSerializerOutputPort,
    private val clock: Clock,
    private val encryption: OutboxPayloadProtectorOutputPort,
    private val passwordHasher: PasswordHasherOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AcademicReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateUserInputPort {
    override fun createUser(request: CreateUserDto): AcademicUserView {
                val (actor, name, emailInput, role, key) = request
        return         transactionRunner.run(TransactionRunDto {
            AcademicAuthorization.requireAdmin(store, actor, true)
            val trimmedName = name.trim()
            val email = emailInput.trim()
            require(trimmedName.isNotEmpty() && trimmedName.length <= 200 && EmailAddress.isValid(email) && role in setOf("teacher", "admin"))
            AcademicIdempotency.execute(idempotency, objectMapper, actor, "createUser", "/users", key, mapOf("name" to trimmedName, "email" to email, "role" to role),
                encode = { receiptCodec.encode(it) },
                decode = receiptCodec::decodeUser,
            ) {
                val now = Instant.now(clock)
                val id = UUID.randomUUID().toString()
                val passwordToken = AcademicTokens.generate()
                val confirmationToken = AcademicTokens.generate()
                val passwordEvent = UUID.randomUUID().toString()
                val confirmationEvent = UUID.randomUUID().toString()
                val passwordPayload = objectMapper.serialize(SerializeJsonDto(mapOf("recipient" to email, "token" to passwordToken, "userId" to id, "purpose" to "password_reset")))
                val confirmationPayload = objectMapper.serialize(SerializeJsonDto(mapOf("recipient" to email, "token" to confirmationToken, "userId" to id, "purpose" to "email_confirmation")))
                val passwordEncrypted = encryption.encrypt(EncryptOutboxPayloadDto(passwordEvent, "identity.password-reset-requested", 1, passwordPayload))
                val confirmationEncrypted = encryption.encrypt(EncryptOutboxPayloadDto(confirmationEvent, "identity.email-confirmation-requested", 1, confirmationPayload))
                store.createUser(AcademicStoreCreateUserDto(
                    ProvisionedIdentity(
                        id,
                        actor.id,
                        trimmedName,
                        email,
                        role,
                        now,
                        passwordHasher.hash(HashPasswordDto(AcademicTokens.generate())),
                        UUID.randomUUID().toString(),
                        AcademicTokens.sha256(passwordToken),
                        passwordEvent,
                        passwordEncrypted.ciphertext,
                        passwordEncrypted.nonce,
                        UUID.randomUUID().toString(),
                        AcademicTokens.sha256(confirmationToken),
                        confirmationEvent,
                        confirmationEncrypted.ciphertext,
                        confirmationEncrypted.nonce,
                        encryption.keyId(OutboxPayloadKeyDto),
                    ),
                ))
            }
        })
    }
}
