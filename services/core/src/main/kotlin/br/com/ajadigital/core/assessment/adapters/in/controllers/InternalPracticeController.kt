package br.com.ajadigital.core.assessment.adapters.`in`.controllers

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.assessment.adapters.`in`.controllers.models.requests.InternalPracticeRequest
import br.com.ajadigital.core.assessment.adapters.`in`.controllers.models.responses.InternalPracticeResponse
import br.com.ajadigital.core.assessment.application.models.CheckPracticeBlockedQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.CheckPracticeBlockedInputPort
import br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.identity.domain.exceptions.InvalidInternalServiceCredential
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@RestController
class InternalPracticeController(
    private val resolveActor: ResolveAuthenticatedActorInputPort,
    private val checkPracticeBlocked: CheckPracticeBlockedInputPort,
    @Value("\${core.internal.questions-token}") serviceToken: String,
) {
    private val expectedToken = serviceToken.toByteArray(StandardCharsets.UTF_8)

    init {
        require(expectedToken.size >= 32) { "Questions service credential must contain at least 32 bytes" }
    }

    @PostMapping("/internal/v1/session/practice")
    fun practice(
        @RequestHeader(name = "X-AJA-Service-Token", required = false) candidateToken: String?,
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestBody request: InternalPracticeRequest,
    ): InternalPracticeResponse {
        val supplied = candidateToken?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        if (!MessageDigest.isEqual(expectedToken, supplied)) throw InvalidInternalServiceCredential()
        val actor = resolveActor.resolve(ResolveStudentSessionDto(cookie))
        if (actor.role !in setOf("student", "teacher", "admin")) throw AcademicForbidden()
        return try {
            InternalPracticeResponse(checkPracticeBlocked.practiceBlocked(CheckPracticeBlockedQuery(actor, request.questionId)))
        } catch (_: AcademicForbidden) {
            throw SessionRequired()
        }
    }
}
