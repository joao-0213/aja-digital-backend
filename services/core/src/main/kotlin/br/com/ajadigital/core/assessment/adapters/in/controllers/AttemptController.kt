package br.com.ajadigital.core.assessment.adapters.`in`.controllers

import br.com.ajadigital.core.assessment.domain.ports.`in`.GetAttemptResultUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.GetAttemptUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListAttemptsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.SaveAttemptAnswersUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.StartAttemptUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.SubmitAttemptUseCase
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.identity.domain.ports.`in`.AuthenticateStudentUseCase
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
class AttemptController(
    private val auth: AuthenticateStudentUseCase,
    private val listAttempts: ListAttemptsUseCase,
    private val startAttempt: StartAttemptUseCase,
    private val getAttempt: GetAttemptUseCase,
    private val saveAttemptAnswers: SaveAttemptAnswersUseCase,
    private val submitAttempt: SubmitAttemptUseCase,
    private val getAttemptResult: GetAttemptResultUseCase,
) {
    @GetMapping("/api/v1/attempts")
    fun attempts(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestParam(defaultValue = "1") page: String,
        @RequestParam(defaultValue = "20") pageSize: String,
        @RequestParam(defaultValue = "id") sort: String,
        @RequestParam(required = false) assessmentId: String?,
        @RequestParam(required = false) studentId: String?,
        request: HttpServletRequest,
    ): Map<String, Any?> {
        require(request.parameterMap.keys.all { it in setOf("page", "pageSize", "sort", "assessmentId", "studentId") })
        return listAttempts.attempts(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, assessmentId, studentId)
    }

    @PostMapping("/api/v1/attempts")
    fun start(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        return ResponseEntity.status(HttpStatus.CREATED).body(startAttempt.start(actor(cookie), key ?: throw IllegalArgumentException(), body))
    }

    @GetMapping("/api/v1/attempts/{id}")
    fun attempt(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return getAttempt.attempt(actor(cookie), id)
    }

    @PatchMapping("/api/v1/attempts/{id}/answers")
    fun saveAnswers(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return saveAttemptAnswers.saveAnswers(actor(cookie), id, body)
    }

    @PostMapping("/api/v1/attempts/{id}/submit")
    fun submit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return submitAttempt.submit(actor(cookie), id, key ?: throw IllegalArgumentException(), body)
    }

    @GetMapping("/api/v1/attempts/{id}/result")
    fun result(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return getAttemptResult.result(actor(cookie), id)
    }

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
}
