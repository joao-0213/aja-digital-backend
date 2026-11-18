package br.com.ajadigital.core.identity.application.usecases

import br.com.ajadigital.core.identity.application.ports.`in`.ResolveSessionInputPort
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import br.com.ajadigital.core.identity.application.models.FindIdentitySessionDto
import br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto
import br.com.ajadigital.core.identity.domain.models.SessionRecord
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

class ResolveSessionUseCase(private val store: IdentityStoreOutputPort, private val clock: Clock) : ResolveSessionInputPort {
    override fun resolve(request: ResolveStudentSessionDto): SessionRecord {
        val cookieValue = request.cookieValue
        if (cookieValue.isNullOrBlank()) throw SessionRequired()
        val session = store.findSession(FindIdentitySessionDto(sha256(cookieValue))) ?: throw SessionRequired()
        if (session.revokedAt != null || !session.user.active || !Instant.now(clock).isBefore(session.expiresAt)) throw SessionRequired()
        return session
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
