package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency
import br.com.ajadigital.core.academic.application.capabilities.AcademicTokens
import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateUserDto
import br.com.ajadigital.core.academic.application.models.AcademicUserView
import br.com.ajadigital.core.academic.application.models.CreateUserDto
import br.com.ajadigital.core.academic.application.models.ProvisionedIdentity
import br.com.ajadigital.core.academic.application.ports.`in`.CreateUserInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicRequestFingerprintOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.contracts.EmailAddress
import br.com.ajadigital.core.identity.application.ports.`in`.HashPasswordInputPort
import br.com.ajadigital.core.outbox.application.contracts.PrepareIdentityNotificationCommand
import br.com.ajadigital.core.outbox.application.ports.`in`.OutboxEventProducerInputPort
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import java.time.Clock
import java.time.Instant
import java.util.UUID

class CreateUserUseCase(
    private val store: AcademicStoreOutputPort,
    private val fingerprints: AcademicRequestFingerprintOutputPort,
    private val clock: Clock,
    private val encryption: OutboxEventProducerInputPort,
    private val passwordHasher: HashPasswordInputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AcademicReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateUserInputPort {
    override fun createUser(request: CreateUserDto): AcademicUserView {
        val actor = request.actor
        val name = request.name
        val emailInput = request.emailInput
        val role = request.role
        val key = request.key
        return transactionRunner.run {
            AcademicAuthorization.requireAdmin(store, actor, true)
            val trimmedName = name.trim()
            val email = emailInput.trim()
            require(trimmedName.isNotEmpty() && trimmedName.length <= MAX_NAME_LENGTH && EmailAddress.isValid(email) && role in setOf("teacher", "admin"))
            AcademicIdempotency.execute(
                idempotency,
                actor,
                "createUser",
                "/users",
                key,
                { fingerprints.createUser(trimmedName, email, role) },
                encode = { receiptCodec.encode(it) },
                decode = receiptCodec::decodeUser,
            ) {
                val now = Instant.now(clock)
                val id = UUID.randomUUID().toString()
                val passwordToken = AcademicTokens.generate()
                val confirmationToken = AcademicTokens.generate()
                val passwordEvent = UUID.randomUUID().toString()
                val confirmationEvent = UUID.randomUUID().toString()
                val passwordEncrypted = encryption.prepareIdentityNotification(PrepareIdentityNotificationCommand(passwordEvent, "identity.password-reset-requested", 1, email, passwordToken, id, "password_reset"))
                val confirmationEncrypted = encryption.prepareIdentityNotification(PrepareIdentityNotificationCommand(confirmationEvent, "identity.email-confirmation-requested", 1, email, confirmationToken, id, "email_confirmation"))
                store.createUser(
                    AcademicStoreCreateUserDto(
                        ProvisionedIdentity(
                            id,
                            actor.id,
                            trimmedName,
                            email,
                            role,
                            now,
                            passwordHasher.hash(AcademicTokens.generate()),
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
                            passwordEncrypted.keyId,
                        ),
                    ),
                )
            }
        }
    }
}

private const val MAX_NAME_LENGTH = 200
