package br.com.ajadigital.core.assessment.adapters.`in`.controllers
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.UpdateSubmissionDto

import br.com.ajadigital.core.assessment.application.models.SubmitSubmissionDto

import br.com.ajadigital.core.assessment.application.models.SaveSubmissionFeedbackDto

import br.com.ajadigital.core.assessment.application.models.PublishSubmissionFeedbackDto

import br.com.ajadigital.core.assessment.application.models.ListSubmissionsDto

import br.com.ajadigital.core.assessment.application.models.GetSubmissionDto

import br.com.ajadigital.core.assessment.application.models.CreateSubmissionDto

import br.com.ajadigital.core.assessment.application.ports.`in`.CreateSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.GetSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListSubmissionsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SaveSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SubmitSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateSubmissionInputPort
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
class SubmissionController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val listSubmissions: ListSubmissionsInputPort,
    private val createSubmission: CreateSubmissionInputPort,
    private val getSubmission: GetSubmissionInputPort,
    private val updateSubmission: UpdateSubmissionInputPort,
    private val submitSubmission: SubmitSubmissionInputPort,
    private val saveSubmissionFeedback: SaveSubmissionFeedbackInputPort,
    private val publishSubmissionFeedback: PublishSubmissionFeedbackInputPort,
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
        return listSubmissions.submissions(ListSubmissionsDto(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, assessmentId, studentId))
    }

    @PostMapping("/api/v1/submissions")
    fun create(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        return ResponseEntity.status(HttpStatus.CREATED).body(createSubmission.create(CreateSubmissionDto(actor(cookie), key ?: throw IllegalArgumentException(), body.toRequestValue())))
    }

    @GetMapping("/api/v1/submissions/{id}")
    fun submission(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return getSubmission.submission(GetSubmissionDto(actor(cookie), id))
    }

    @PatchMapping("/api/v1/submissions/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return updateSubmission.update(UpdateSubmissionDto(actor(cookie), id, body.toRequestValue()))
    }

    @PostMapping("/api/v1/submissions/{id}/submit")
    fun submit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return submitSubmission.submit(SubmitSubmissionDto(actor(cookie), id, key ?: throw IllegalArgumentException(), body.toRequestValue()))
    }

    @PatchMapping("/api/v1/submissions/{id}/feedback")
    fun saveFeedback(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return saveSubmissionFeedback.saveFeedback(SaveSubmissionFeedbackDto(actor(cookie), id, body.toRequestValue()))
    }

    @PostMapping("/api/v1/submissions/{id}/publish-feedback")
    fun publishFeedback(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return publishSubmissionFeedback.publishFeedback(PublishSubmissionFeedbackDto(actor(cookie), id, key ?: throw IllegalArgumentException(), body.toRequestValue()))
    }

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))
}
