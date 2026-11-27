package br.com.ajadigital.core.assessment.adapters.`in`.controllers
import br.com.ajadigital.core.assessment.application.models.*
import br.com.ajadigital.core.assessment.adapters.serialization.AttemptRepresentation
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort

import br.com.ajadigital.core.assessment.application.models.SubmitAttemptCommand

import br.com.ajadigital.core.assessment.application.models.StartAttemptCommand

import br.com.ajadigital.core.assessment.application.models.SaveAttemptAnswersCommand

import br.com.ajadigital.core.assessment.application.models.ListAttemptsQuery

import br.com.ajadigital.core.assessment.application.models.GetAttemptResultQuery

import br.com.ajadigital.core.assessment.application.models.GetAttemptQuery

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
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import java.security.MessageDigest

@RestController
class AttemptController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val listAttempts: ListAttemptsInputPort,
    private val startAttempt: StartAttemptInputPort,
    private val getAttempt: GetAttemptInputPort,
    private val saveAttemptAnswers: SaveAttemptAnswersInputPort,
    private val submitAttempt: SubmitAttemptInputPort,
    private val getAttemptResult: GetAttemptResultInputPort,
    private val serializer: JsonSerializerOutputPort,
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
        val found = listAttempts.attempts(ListAttemptsQuery(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, assessmentId, studentId))
        return AttemptRepresentation.page(found)
    }

    @PostMapping("/api/v1/attempts")
    fun start(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        val actor = actor(cookie)
        val idempotencyKey = key ?: throw IllegalArgumentException()
        val value = body.toRequestValue()
        val parsed = value as? Map<*, *>
        val commandBody = parsed?.let { StartAttemptBody(it["assessmentId"] as? String, fieldsMatch(it, setOf("assessmentId"))) }
        val hash = requestHash(value)
        return ResponseEntity.status(HttpStatus.CREATED).body(AttemptRepresentation.attempt(startAttempt.start(StartAttemptCommand(actor, idempotencyKey, commandBody, hash))))
    }

    @GetMapping("/api/v1/attempts/{id}")
    fun attempt(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return AttemptRepresentation.attempt(getAttempt.attempt(GetAttemptQuery(actor(cookie), id)))
    }

    @PatchMapping("/api/v1/attempts/{id}/answers")
    fun saveAnswers(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        val actor = actor(cookie)
        val value = body.toRequestValue()
        val parsed = value as? Map<*, *>
        val commandBody = parsed?.let {
            val rawAnswers = it["answers"] as? Map<*, *>
            var answersValid = rawAnswers != null
            val answers = LinkedHashMap<String, Int?>()
            rawAnswers?.forEach { (rawId, rawAnswer) ->
                val questionId = rawId as? String
                val answer = if (rawAnswer == null) null else integerValue(rawAnswer)?.takeIf { index -> index >= 0 }
                if (questionId == null || rawAnswer != null && answer == null) answersValid = false
                else answers[questionId] = answer
            }
            SaveAttemptAnswersBody(
                expectedRevision = integerValue(it["expectedRevision"]),
                answers = answers.takeIf { answersValid },
                fieldsValid = fieldsMatch(it, setOf("expectedRevision", "answers")),
                answersValid = answersValid,
            )
        }
        return AttemptRepresentation.attempt(saveAttemptAnswers.saveAnswers(SaveAttemptAnswersCommand(actor, id, commandBody)))
    }

    @PostMapping("/api/v1/attempts/{id}/submit")
    fun submit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        val actor = actor(cookie)
        val idempotencyKey = key ?: throw IllegalArgumentException()
        val value = body.toRequestValue()
        val parsed = value as? Map<*, *>
        val commandBody = parsed?.let { SubmitAttemptBody(integerValue(it["expectedRevision"]), fieldsMatch(it, setOf("expectedRevision"))) }
        val hash = requestHash(value)
        return AttemptRepresentation.result(submitAttempt.submit(SubmitAttemptCommand(actor, id, idempotencyKey, commandBody, hash)))
    }

    @GetMapping("/api/v1/attempts/{id}/result")
    fun result(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return AttemptRepresentation.result(getAttemptResult.result(GetAttemptResultQuery(actor(cookie), id)))
    }

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))

    private fun requestHash(value: Any?) = MessageDigest.getInstance("SHA-256").digest(serializer.serialize(SerializeJsonDto(value))).joinToString("") { "%02x".format(it) }
    private fun integerValue(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        else -> null
    }

    private fun fieldsMatch(fields: Map<*, *>, expected: Set<String>) = fields.keys.filterIsInstance<String>().toSet() == expected

}
