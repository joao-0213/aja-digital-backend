package br.com.ajadigital.core.identity.application.usecases

import br.com.ajadigital.core.identity.application.ports.`in`.LoginInputPort
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import br.com.ajadigital.core.identity.domain.exceptions.InvalidCredentials
import br.com.ajadigital.core.identity.application.models.AuthenticatedSession
import br.com.ajadigital.core.identity.application.models.LoginCommand
import br.com.ajadigital.core.identity.application.models.FindLoginIdentityDto
import br.com.ajadigital.core.identity.application.models.RecordLoginFailureDto
import br.com.ajadigital.core.identity.application.models.RotateIdentitySessionDto
import br.com.ajadigital.core.identity.domain.models.SessionRecord
import br.com.ajadigital.core.identity.application.models.VerifyPasswordDto
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

class LoginUseCase(
    private val store: IdentityStoreOutputPort,
    private val passwordHasher: PasswordHasherOutputPort,
    private val clock: Clock,
) : LoginInputPort {
    override fun login(request: LoginCommand): AuthenticatedSession {
        val command = request
        val identity = store.findLoginIdentity(FindLoginIdentityDto(command.email.trim().lowercase()))
        if (identity == null || !passwordHasher.matches(VerifyPasswordDto(command.password, identity.passwordHash)) || !identity.user.active) {
            store.recordLoginFailure(RecordLoginFailureDto(command.previousSessionCookie?.takeIf(String::isNotBlank)?.let(::sha256)))
            throw InvalidCredentials()
        }
        val now = Instant.now(clock)
        val cookieValue = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        val session = SessionRecord(UUID.randomUUID().toString(), sha256(cookieValue), identity.user, now, now.plus(Duration.ofHours(8)))
        val previousHash = command.previousSessionCookie?.takeIf(String::isNotBlank)?.let(::sha256)
        if (!store.rotateSession(RotateIdentitySessionDto(session, previousHash, identity.passwordHash))) {
            store.recordLoginFailure(RecordLoginFailureDto(previousHash))
            throw InvalidCredentials()
        }
        return AuthenticatedSession(session, cookieValue)
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
