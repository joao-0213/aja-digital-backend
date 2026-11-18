package br.com.ajadigital.core.identity.application.usecases
import br.com.ajadigital.core.identity.application.models.AcceptedResponse
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto

import br.com.ajadigital.core.identity.domain.models.User
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenExpired
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenInvalid
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.identity.application.ports.`in`.RequestAuthTokenInputPort
import br.com.ajadigital.core.identity.application.models.AuthTokenIssue
import br.com.ajadigital.core.identity.application.models.TokenFailure
import br.com.ajadigital.core.identity.application.models.RequestAuthTokenDto
import br.com.ajadigital.core.identity.application.models.ConfirmEmailTokenDto
import br.com.ajadigital.core.identity.application.models.FindLoginIdentityDto
import br.com.ajadigital.core.identity.application.models.RecordIdentityAuditDto
import br.com.ajadigital.core.identity.application.models.IssueAuthTokenDto
import br.com.ajadigital.core.identity.application.models.ConfirmEmailDto
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID


class RequestAuthTokenUseCase(
    private val store: IdentityStoreOutputPort,
    private val objectMapper: JsonSerializerOutputPort,
    private val clock: Clock,
    private val encryption: OutboxPayloadProtectorOutputPort,
) : RequestAuthTokenInputPort {
    override fun request(request: RequestAuthTokenDto): AcceptedResponse {
        val (emailInput, purpose) = request
                require(purpose == "email_confirmation" || purpose == "password_reset")
        val email = emailInput.trim()
        require(EmailAddress.isValid(email))
        val requestEvent = if (purpose == "email_confirmation") "auth.email_confirmation_request" else "auth.password_reset_request"
        val identity = store.findLoginIdentity(FindLoginIdentityDto(email.lowercase())) ?: run {
            store.recordAudit(RecordIdentityAuditDto(requestEvent, "accepted"))
            return AcceptedResponse()
        }
        if (purpose == "email_confirmation" && identity.user.emailConfirmedAt != null) {
            store.recordAudit(RecordIdentityAuditDto(requestEvent, "accepted"))
            return AcceptedResponse()
        }

        val now = Instant.now(clock)
        val id = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()
        val eventType = if (purpose == "email_confirmation") "identity.email-confirmation-requested" else "identity.password-reset-requested"
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        val payload = objectMapper.serialize(SerializeJsonDto(mapOf("recipient" to identity.user.email, "token" to token, "userId" to identity.user.id, "purpose" to purpose)))
        val encrypted = encryption.encrypt(EncryptOutboxPayloadDto(eventId, eventType, 1, payload))
        store.issueAuthToken(
            IssueAuthTokenDto(AuthTokenIssue(
                id, identity.user.id, purpose, sha256(token), identity.user.email, now, now.plus(Duration.ofMinutes(30)),
                eventId, eventType, encrypted.ciphertext, encrypted.nonce, encrypted.keyId,
            )),
        )
        return AcceptedResponse()
    }

    override fun confirmEmail(request: ConfirmEmailTokenDto): User {
        val token = request.token
        require(token.isNotBlank() && token.length <= 256)
        val result = store.confirmEmail(ConfirmEmailDto(sha256(token)))
        return result.user ?: when (result.failure) {
            TokenFailure.EXPIRED -> throw AuthTokenExpired()
            else -> throw AuthTokenInvalid()
        }
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
