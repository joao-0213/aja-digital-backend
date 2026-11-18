package br.com.ajadigital.core.assessment.adapters.`in`.controllers
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentDto

import br.com.ajadigital.core.assessment.application.models.PublishAssessmentDto

import br.com.ajadigital.core.assessment.application.models.ListAssessmentsDto

import br.com.ajadigital.core.assessment.application.models.GetAssessmentDto

import br.com.ajadigital.core.assessment.application.models.CreateAssessmentDto

import br.com.ajadigital.core.assessment.application.ports.`in`.CreateAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateAssessmentInputPort
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
class AssessmentController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val listAssessments: ListAssessmentsInputPort,
    private val createAssessment: CreateAssessmentInputPort,
    private val getAssessment: GetAssessmentInputPort,
    private val updateAssessment: UpdateAssessmentInputPort,
    private val publishAssessment: PublishAssessmentInputPort,
) {
    @GetMapping("/api/v1/assessments")
    fun assessments(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestParam(defaultValue = "1") page: String,
        @RequestParam(defaultValue = "20") pageSize: String,
        @RequestParam(defaultValue = "id") sort: String,
        @RequestParam(required = false) subjectId: String?,
        @RequestParam(required = false) moduleId: String?,
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) status: String?,
        request: HttpServletRequest,
    ): Map<String, Any?> {
        require(request.parameterMap.keys.all { it in setOf("page", "pageSize", "sort", "subjectId", "moduleId", "kind", "status") })
        return listAssessments.assessments(ListAssessmentsDto(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, subjectId, moduleId, kind, status))
    }

    @PostMapping("/api/v1/assessments")
    fun create(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        return ResponseEntity.status(HttpStatus.CREATED).body(createAssessment.create(CreateAssessmentDto(actor(cookie), key ?: throw IllegalArgumentException(), body.toRequestValue())))
    }

    @GetMapping("/api/v1/assessments/{id}")
    fun assessment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest) = run {
        require(request.parameterMap.isEmpty())
        getAssessment.assessment(GetAssessmentDto(actor(cookie), id))
    }

    @PatchMapping("/api/v1/assessments/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest) = run {
        require(request.parameterMap.isEmpty())
        updateAssessment.update(UpdateAssessmentDto(actor(cookie), id, key ?: throw IllegalArgumentException(), body.toRequestValue()))
    }

    @PostMapping("/api/v1/assessments/{id}/publish")
    fun publish(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest) = run {
        require(request.parameterMap.isEmpty())
        publishAssessment.publish(PublishAssessmentDto(actor(cookie), id, key ?: throw IllegalArgumentException(), body.get("expectedRevision")?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue() ?: throw IllegalArgumentException(), body.toRequestValue()))
    }

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))
}
