package br.com.ajadigital.core.identity.domain.usecases
import br.com.ajadigital.core.identity.domain.models.AuthenticatedSession
import br.com.ajadigital.core.identity.domain.exceptions.InvalidCredentials
import br.com.ajadigital.core.identity.domain.models.LoginCommand
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired

import br.com.ajadigital.core.identity.domain.ports.`in`.AuthenticateStudentUseCase
import br.com.ajadigital.core.identity.domain.ports.out.IdentityStore
import br.com.ajadigital.core.identity.domain.ports.out.PasswordHasher
import br.com.ajadigital.core.identity.domain.ports.out.SessionRecord
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID


class AuthenticateStudent(
    private val store: IdentityStore,
    private val passwordHasher: PasswordHasher,
    private val clock: Clock,
) : AuthenticateStudentUseCase {
    override fun login(command: LoginCommand): AuthenticatedSession {
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

    override fun resolve(cookieValue: String?): SessionRecord {
        if (cookieValue.isNullOrBlank()) throw SessionRequired()
        val session = store.findSession(sha256(cookieValue)) ?: throw SessionRequired()
        if (session.revokedAt != null || !session.user.active || !Instant.now(clock).isBefore(session.expiresAt)) {
            throw SessionRequired()
        }
        return session
    }

    override fun logout(cookieValue: String?) {
        store.revokeSession(cookieValue?.takeIf(String::isNotBlank)?.let(::sha256))
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
