package br.com.ajadigital.core.identity.domain.usecases
import br.com.ajadigital.core.identity.domain.models.AcceptedResponse
import br.com.ajadigital.core.identity.adapters.out.security.OutboxEncryption

import br.com.ajadigital.core.identity.domain.models.User
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.identity.domain.ports.out.AuthTokenIssue
import br.com.ajadigital.core.identity.domain.ports.out.IdentityStore
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID


class RequestAuthToken(
    private val store: IdentityStore,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val encryption: OutboxEncryption,
) {
    fun request(emailInput: String, purpose: String): AcceptedResponse {
        require(purpose == "email_confirmation" || purpose == "password_reset")
        val email = emailInput.trim()
        require(EmailAddress.isValid(email))
        val requestEvent = if (purpose == "email_confirmation") "auth.email_confirmation_request" else "auth.password_reset_request"
        val identity = store.findLoginIdentity(email.lowercase()) ?: run {
            store.recordAudit(requestEvent, "accepted")
            return AcceptedResponse()
        }
        if (purpose == "email_confirmation" && identity.user.emailConfirmedAt != null) {
            store.recordAudit(requestEvent, "accepted")
            return AcceptedResponse()
        }

        val now = Instant.now(clock)
        val id = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()
        val eventType = if (purpose == "email_confirmation") "identity.email-confirmation-requested" else "identity.password-reset-requested"
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        val payload = objectMapper.writeValueAsBytes(mapOf("recipient" to identity.user.email, "token" to token, "userId" to identity.user.id, "purpose" to purpose))
        val encrypted = encryption.encrypt(eventId, eventType, 1, payload)
        store.issueAuthToken(
            AuthTokenIssue(
                id, identity.user.id, purpose, sha256(token), identity.user.email, now, now.plus(Duration.ofMinutes(30)),
                eventId, eventType, encrypted.ciphertext, encrypted.nonce, encrypted.keyId,
            ),
        )
        return AcceptedResponse()
    }

    fun confirmEmail(token: String): User {
        require(token.isNotBlank() && token.length <= 256)
        return store.confirmEmail(sha256(token)).toUserOrThrow()
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
