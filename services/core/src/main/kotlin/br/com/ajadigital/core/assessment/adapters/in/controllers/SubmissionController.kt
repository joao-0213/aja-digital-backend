package br.com.ajadigital.core.assessment.adapters.`in`.controllers

import br.com.ajadigital.core.assessment.domain.ports.`in`.CreateSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.GetSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListSubmissionsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.PublishSubmissionFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.SaveSubmissionFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.SubmitSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.UpdateSubmissionUseCase
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
class SubmissionController(
    private val auth: AuthenticateStudentUseCase,
    private val listSubmissions: ListSubmissionsUseCase,
    private val createSubmission: CreateSubmissionUseCase,
    private val getSubmission: GetSubmissionUseCase,
    private val updateSubmission: UpdateSubmissionUseCase,
    private val submitSubmission: SubmitSubmissionUseCase,
    private val saveSubmissionFeedback: SaveSubmissionFeedbackUseCase,
    private val publishSubmissionFeedback: PublishSubmissionFeedbackUseCase,
) {
    @GetMapping("/api/v1/submissions")
    fun submissions(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestParam(defaultValue = "1") page: String,
        @RequestParam(defaultValue = "20") pageSize: String,
        @RequestParam(defaultValue = "id") sort: String,
        @RequestParam(required = false) assessmentId: String?,
        @RequestParam(required = false) studentId: String?,
        request: HttpServletRequest,
    ): Map<String, Any?> {
        require(request.parameterMap.keys.all { it in setOf("page", "pageSize", "sort", "assessmentId", "studentId") })
        return listSubmissions.submissions(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, assessmentId, studentId)
    }

    @PostMapping("/api/v1/submissions")
    fun create(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        return ResponseEntity.status(HttpStatus.CREATED).body(createSubmission.create(actor(cookie), key ?: throw IllegalArgumentException(), body))
    }

    @GetMapping("/api/v1/submissions/{id}")
    fun submission(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return getSubmission.submission(actor(cookie), id)
    }

    @PatchMapping("/api/v1/submissions/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return updateSubmission.update(actor(cookie), id, body)
    }

    @PostMapping("/api/v1/submissions/{id}/submit")
    fun submit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return submitSubmission.submit(actor(cookie), id, key ?: throw IllegalArgumentException(), body)
    }

    @PatchMapping("/api/v1/submissions/{id}/feedback")
    fun saveFeedback(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return saveSubmissionFeedback.saveFeedback(actor(cookie), id, body)
    }

    @PostMapping("/api/v1/submissions/{id}/publish-feedback")
    fun publishFeedback(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return publishSubmissionFeedback.publishFeedback(actor(cookie), id, key ?: throw IllegalArgumentException(), body)
    }

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
}
