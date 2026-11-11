package br.com.ajadigital.core.application.identity

import br.com.ajadigital.core.domain.identity.User
import br.com.ajadigital.core.ports.out.IdentityStore
import br.com.ajadigital.core.ports.out.PasswordHasher
import br.com.ajadigital.core.ports.out.SessionRecord
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

class InvalidCredentials : RuntimeException()
class SessionRequired : RuntimeException()
data class LoginCommand(val email: String, val password: String, val previousSessionCookie: String?)
data class AuthenticatedSession(val session: SessionRecord, val cookieValue: String)

class AuthenticateStudent(
    private val store: IdentityStore,
    private val passwordHasher: PasswordHasher,
    private val clock: Clock,
) {
    fun login(command: LoginCommand): AuthenticatedSession {
        val identity = store.findLoginIdentity(command.email.trim().lowercase())
        if (identity == null || !passwordHasher.matches(command.password, identity.passwordHash) || !identity.user.active) {
            store.recordLoginFailure(command.previousSessionCookie?.takeIf(String::isNotBlank)?.let(::sha256))
            throw InvalidCredentials()
        }
        val now = Instant.now(clock)
        val cookieValue = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        val session = SessionRecord(
            id = UUID.randomUUID().toString(),
            tokenHash = sha256(cookieValue),
            user = identity.user,
            createdAt = now,
            expiresAt = now.plus(Duration.ofHours(8)),
        )
        val previousHash = command.previousSessionCookie?.takeIf(String::isNotBlank)?.let(::sha256)
        if (!store.rotateSession(session, previousHash, identity.passwordHash)) {
            store.recordLoginFailure(previousHash)
            throw InvalidCredentials()
        }
        return AuthenticatedSession(session, cookieValue)
    }

    fun resolve(cookieValue: String?): SessionRecord {
        if (cookieValue.isNullOrBlank()) throw SessionRequired()
        val session = store.findSession(sha256(cookieValue)) ?: throw SessionRequired()
        if (session.revokedAt != null || !session.user.active || !Instant.now(clock).isBefore(session.expiresAt)) {
            throw SessionRequired()
        }
        return session
    }

    fun logout(cookieValue: String?) {
        store.revokeSession(cookieValue?.takeIf(String::isNotBlank)?.let(::sha256))
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class SessionResponse(val user: User, val expiresAt: Instant, val sessionId: String)
fun SessionRecord.toResponse() = SessionResponse(user, expiresAt, id)
