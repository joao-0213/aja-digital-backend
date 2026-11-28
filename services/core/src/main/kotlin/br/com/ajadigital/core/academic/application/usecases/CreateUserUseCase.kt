package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency
import br.com.ajadigital.core.academic.application.capabilities.AcademicTokens

import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateUserDto

import br.com.ajadigital.core.academic.application.models.CreateUserDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.CreateUserInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.application.models.ProvisionedIdentity
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.outbox.domain.models.EncryptIdentityNotificationDto
import br.com.ajadigital.core.outbox.domain.models.OutboxPayloadKeyDto
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import br.com.ajadigital.core.identity.application.models.HashPasswordDto
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicRequestFingerprintOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import java.time.Clock
import java.time.Instant
import java.util.UUID

class CreateUserUseCase(
    private val store: AcademicStoreOutputPort,
    private val fingerprints: AcademicRequestFingerprintOutputPort,
    private val clock: Clock,
    private val encryption: OutboxPayloadProtectorOutputPort,
    private val passwordHasher: PasswordHasherOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AcademicReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateUserInputPort {
    override fun createUser(request: CreateUserDto): AcademicUserView {
                val (actor, name, emailInput, role, key) = request
        return transactionRunner.run {
            AcademicAuthorization.requireAdmin(store, actor, true)
            val trimmedName = name.trim()
            val email = emailInput.trim()
            require(trimmedName.isNotEmpty() && trimmedName.length <= 200 && EmailAddress.isValid(email) && role in setOf("teacher", "admin"))
            AcademicIdempotency.execute(idempotency, actor, "createUser", "/users", key, { fingerprints.createUser(trimmedName, email, role) },
                encode = { receiptCodec.encode(it) },
                decode = receiptCodec::decodeUser,
            ) {
                val now = Instant.now(clock)
                val id = UUID.randomUUID().toString()
                val passwordToken = AcademicTokens.generate()
                val confirmationToken = AcademicTokens.generate()
                val passwordEvent = UUID.randomUUID().toString()
                val confirmationEvent = UUID.randomUUID().toString()
                val passwordEncrypted = encryption.encryptIdentityNotification(EncryptIdentityNotificationDto(passwordEvent, "identity.password-reset-requested", 1, email, passwordToken, id, "password_reset"))
                val confirmationEncrypted = encryption.encryptIdentityNotification(EncryptIdentityNotificationDto(confirmationEvent, "identity.email-confirmation-requested", 1, email, confirmationToken, id, "email_confirmation"))
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
        }
    }
}
