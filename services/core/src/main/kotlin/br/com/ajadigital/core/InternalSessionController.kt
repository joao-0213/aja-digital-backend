package br.com.ajadigital.core

import br.com.ajadigital.core.application.identity.AuthenticateStudent
import br.com.ajadigital.core.application.academic.AcademicActor
import br.com.ajadigital.core.application.academic.ManageAttempts
import br.com.ajadigital.core.application.identity.SessionRequired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import org.springframework.web.bind.annotation.RequestHeader


@RestController
class InternalSessionController(
    private val authenticateStudent: AuthenticateStudent,
    private val attempts: ManageAttempts,
    @Value("\${core.internal.questions-token}") serviceToken: String,
) {
    private val expectedToken = serviceToken.toByteArray(StandardCharsets.UTF_8)

    init {
        require(expectedToken.size >= 32) { "Questions service credential must contain at least 32 bytes" }
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

    @PostMapping("/internal/v1/session/practice")
    fun practice(
        @RequestHeader(name = "X-AJA-Service-Token", required = false) candidateToken: String?,
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestBody request: InternalPracticeRequest,
    ): InternalPracticeResponse {
        val supplied = candidateToken?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        if (!MessageDigest.isEqual(expectedToken, supplied)) throw InvalidInternalServiceCredential()
        val session = authenticateStudent.resolve(cookie)
        if (session.user.role !in setOf("student", "teacher", "admin")) throw br.com.ajadigital.core.application.academic.AcademicForbidden()
        val actor = AcademicActor(session.user.id, session.user.role, session.user.active, session.user.emailConfirmedAt, session.tokenHash)
        return try {
            InternalPracticeResponse(attempts.practiceBlocked(actor, request.questionId))
        } catch (_: br.com.ajadigital.core.application.academic.AcademicForbidden) {
            throw SessionRequired()
        }
    }
}
