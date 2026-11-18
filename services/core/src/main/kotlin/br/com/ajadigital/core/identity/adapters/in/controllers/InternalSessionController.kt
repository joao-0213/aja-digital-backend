package br.com.ajadigital.core.identity.adapters.`in`.controllers

import br.com.ajadigital.core.assessment.application.models.CheckPracticeBlockedDto
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.InternalPracticeRequest
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.responses.InternalPracticeResponse
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.responses.InternalSessionResponse
import br.com.ajadigital.core.identity.domain.exceptions.InvalidInternalServiceCredential

import br.com.ajadigital.core.identity.application.ports.`in`.ResolveSessionInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.CheckPracticeBlockedInputPort
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
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
    private val authenticateStudent: ResolveSessionInputPort,
    private val resolveActor: ResolveAuthenticatedActorInputPort,
    private val checkPracticeBlocked: CheckPracticeBlockedInputPort,
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
        val session = authenticateStudent.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))
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
        val actor = resolveActor.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))
        if (actor.role !in setOf("student", "teacher", "admin")) throw br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden()
        return try {
            InternalPracticeResponse(checkPracticeBlocked.practiceBlocked(CheckPracticeBlockedDto(actor, request.questionId)))
        } catch (_: br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden) {
            throw SessionRequired()
        }
    }
}
