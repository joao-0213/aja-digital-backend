package br.com.ajadigital.core.assessment.adapters.`in`.controllers
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.SubmitAttemptDto

import br.com.ajadigital.core.assessment.application.models.StartAttemptDto

import br.com.ajadigital.core.assessment.application.models.SaveAttemptAnswersDto

import br.com.ajadigital.core.assessment.application.models.ListAttemptsDto

import br.com.ajadigital.core.assessment.application.models.GetAttemptResultDto

import br.com.ajadigital.core.assessment.application.models.GetAttemptDto

import br.com.ajadigital.core.assessment.application.ports.`in`.GetAttemptResultInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAttemptInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAttemptsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SaveAttemptAnswersInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.StartAttemptInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SubmitAttemptInputPort
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
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
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.toRequestValue

@RestController
class AttemptController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val listAttempts: ListAttemptsInputPort,
    private val startAttempt: StartAttemptInputPort,
    private val getAttempt: GetAttemptInputPort,
    private val saveAttemptAnswers: SaveAttemptAnswersInputPort,
    private val submitAttempt: SubmitAttemptInputPort,
    private val getAttemptResult: GetAttemptResultInputPort,
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
        return listAttempts.attempts(ListAttemptsDto(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, assessmentId, studentId))
    }

    @PostMapping("/api/v1/attempts")
    fun start(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        return ResponseEntity.status(HttpStatus.CREATED).body(startAttempt.start(StartAttemptDto(actor(cookie), key ?: throw IllegalArgumentException(), body.toRequestValue())))
    }

    @GetMapping("/api/v1/attempts/{id}")
    fun attempt(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return getAttempt.attempt(GetAttemptDto(actor(cookie), id))
    }

    @PatchMapping("/api/v1/attempts/{id}/answers")
    fun saveAnswers(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return saveAttemptAnswers.saveAnswers(SaveAttemptAnswersDto(actor(cookie), id, body.toRequestValue()))
    }

    @PostMapping("/api/v1/attempts/{id}/submit")
    fun submit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return submitAttempt.submit(SubmitAttemptDto(actor(cookie), id, key ?: throw IllegalArgumentException(), body.toRequestValue()))
    }

    @GetMapping("/api/v1/attempts/{id}/result")
    fun result(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return getAttemptResult.result(GetAttemptResultDto(actor(cookie), id))
    }

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))
}
