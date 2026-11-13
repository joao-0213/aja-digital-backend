package br.com.ajadigital.core

import br.com.ajadigital.core.application.academic.AcademicActor
import br.com.ajadigital.core.application.academic.ManageAttempts
import br.com.ajadigital.core.application.identity.AuthenticateStudent
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import jakarta.servlet.http.HttpServletRequest
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
class AttemptController(private val auth: AuthenticateStudent, private val attempts: ManageAttempts) {
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
        return attempts.attempts(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, assessmentId, studentId)
    }

    @PostMapping("/api/v1/attempts")
    fun start(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        return ResponseEntity.status(HttpStatus.CREATED).body(attempts.start(actor(cookie), key ?: throw IllegalArgumentException(), body))
    }

    @GetMapping("/api/v1/attempts/{id}")
    fun attempt(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return attempts.attempt(actor(cookie), id)
    }

    @PatchMapping("/api/v1/attempts/{id}/answers")
    fun saveAnswers(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return attempts.saveAnswers(actor(cookie), id, body)
    }

    @PostMapping("/api/v1/attempts/{id}/submit")
    fun submit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return attempts.submit(actor(cookie), id, key ?: throw IllegalArgumentException(), body)
    }

    @GetMapping("/api/v1/attempts/{id}/result")
    fun result(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return attempts.result(actor(cookie), id)
    }

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
}
