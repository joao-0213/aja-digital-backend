package br.com.ajadigital.core.identity.adapters.`in`.controllers

import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.responses.InternalSessionResponse
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveSessionInputPort
import br.com.ajadigital.core.identity.domain.exceptions.InvalidInternalServiceCredential
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@RestController
class InternalSessionController(
    private val authenticateStudent: ResolveSessionInputPort,
    @Value("\${core.internal.questions-token}") serviceToken: String,
) {
    private val expectedToken = serviceToken.toByteArray(StandardCharsets.UTF_8)

    init {
        require(expectedToken.size >= MIN_SERVICE_TOKEN_BYTES) { "Questions service credential must contain at least 32 bytes" }
    }

    @GetMapping("/internal/v1/session")
    fun inspect(
        @RequestHeader(name = "X-AJA-Service-Token", required = false) candidateToken: String?,
        @CookieValue(name = "aja_session", required = false) cookie: String?,
    ): InternalSessionResponse {
        val supplied = candidateToken?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        if (!MessageDigest.isEqual(expectedToken, supplied)) throw InvalidInternalServiceCredential()
        val session = authenticateStudent.resolve(cookie)
        return InternalSessionResponse(session.user.id, session.id, session.user.role, session.user.active, session.user.emailConfirmedAt)
    }
}

private const val MIN_SERVICE_TOKEN_BYTES = 32
