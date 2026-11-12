package br.com.ajadigital.core

import br.com.ajadigital.core.application.academic.AcademicActor
import br.com.ajadigital.core.application.academic.ManageAssessments
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
class AssessmentController(private val auth: AuthenticateStudent, private val assessments: ManageAssessments) {
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
        return assessments.assessments(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, subjectId, moduleId, kind, status)
    }

    @PostMapping("/api/v1/assessments")
    fun create(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        return ResponseEntity.status(HttpStatus.CREATED).body(assessments.create(actor(cookie), key ?: throw IllegalArgumentException(), body))
    }

    @GetMapping("/api/v1/assessments/{id}")
    fun assessment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest) = run {
        require(request.parameterMap.isEmpty())
        assessments.assessment(actor(cookie), id)
    }

    @PatchMapping("/api/v1/assessments/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest) = run {
        require(request.parameterMap.isEmpty())
        assessments.update(actor(cookie), id, key ?: throw IllegalArgumentException(), body)
    }

    @PostMapping("/api/v1/assessments/{id}/publish")
    fun publish(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest) = run {
        require(request.parameterMap.isEmpty())
        assessments.publish(actor(cookie), id, key ?: throw IllegalArgumentException(), body.get("expectedRevision")?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue() ?: throw IllegalArgumentException(), body)
    }

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
}
