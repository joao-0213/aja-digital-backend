package br.com.ajadigital.core.ports.out

import br.com.ajadigital.core.domain.identity.User
import java.time.Instant

data class RegistrationRecords(
    val user: User,
    val passwordHash: String,
    val tokenId: String,
    val tokenHash: String,
    val tokenExpiresAt: Instant,
    val eventId: String,
    val encryptedPayload: ByteArray,
    val nonce: ByteArray,
    val keyId: String,
)

interface IdentityStore {
    fun emailExists(normalizedEmail: String): Boolean
    fun recordAudit(eventType: String, outcome: String, subjectUserId: String? = null)
    fun recordLoginFailure(previousTokenHash: String?)
    fun createRegistration(records: RegistrationRecords)
    fun findLoginIdentity(normalizedEmail: String): LoginIdentity?
    fun rotateSession(record: SessionRecord, previousTokenHash: String?, expectedPasswordHash: String): Boolean
    fun findSession(tokenHash: String): SessionRecord?
    fun revokeSession(tokenHash: String?)
    fun issueAuthToken(issue: AuthTokenIssue): Boolean
    fun confirmEmail(tokenHash: String): AuthTokenResult
    fun resetPassword(tokenHash: String, passwordHash: String): TokenFailure?
    fun updateProfile(sessionTokenHash: String, expectedRevision: Int, name: String?): ProfileUpdateResult
}

data class LoginIdentity(val user: User, val passwordHash: String)
data class SessionRecord(
    val id: String,
    val tokenHash: String,
    val user: User,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
)

data class AuthTokenIssue(
    val id: String,
    val userId: String,
    val purpose: String,
    val tokenHash: String,
    val emailAtIssue: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val eventId: String,
    val eventType: String,
    val encryptedPayload: ByteArray,
    val nonce: ByteArray,
    val keyId: String,
)

data class AuthTokenResult(val user: User? = null, val failure: TokenFailure? = null)
enum class TokenFailure { INVALID, EXPIRED }

sealed interface ProfileUpdateResult {
    data class Updated(val user: User) : ProfileUpdateResult
    data class RevisionConflict(val currentRevision: Int) : ProfileUpdateResult
    data object SessionInvalid : ProfileUpdateResult
}
