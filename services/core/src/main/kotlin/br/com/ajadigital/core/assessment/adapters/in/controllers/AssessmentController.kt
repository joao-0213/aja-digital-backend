package br.com.ajadigital.core.assessment.adapters.`in`.controllers
import br.com.ajadigital.core.assessment.adapters.serialization.AssessmentRepresentation
import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery
import br.com.ajadigital.core.assessment.application.models.ListAssessmentsQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.CreateAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateAssessmentInputPort
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
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

@RestController
class AssessmentController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val listAssessments: ListAssessmentsInputPort,
    private val createAssessment: CreateAssessmentInputPort,
    private val getAssessment: GetAssessmentInputPort,
    private val updateAssessment: UpdateAssessmentInputPort,
    private val publishAssessment: PublishAssessmentInputPort,
    private val requests: AssessmentRequestReader,
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
        val result = listAssessments.assessments(ListAssessmentsQuery(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, subjectId, moduleId, kind, status))
        return mapOf("items" to result.items.map(AssessmentRepresentation::response), "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }

    @PostMapping("/api/v1/assessments")
    fun create(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        val actor = actor(cookie)
        val requestKey = requireNotNull(key)
        AssessmentPolicy.writer(actor.role, actor.active)
        val created = createAssessment.create(requests.create(actor, requestKey, body))
        return ResponseEntity.status(HttpStatus.CREATED).body(AssessmentRepresentation.response(created))
    }

    @GetMapping("/api/v1/assessments/{id}")
    fun assessment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest) = run {
        require(request.parameterMap.isEmpty())
        AssessmentRepresentation.response(getAssessment.assessment(GetAssessmentQuery(actor(cookie), id)))
    }

    @PatchMapping("/api/v1/assessments/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest) = run {
        require(request.parameterMap.isEmpty())
        val actor = actor(cookie)
        val requestKey = requireNotNull(key)
        AssessmentPolicy.writer(actor.role, actor.active)
        val updated = updateAssessment.update(requests.update(actor, id, requestKey, body))
        AssessmentRepresentation.response(updated)
    }

    @PostMapping("/api/v1/assessments/{id}/publish")
    fun publish(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest) = run {
        require(request.parameterMap.isEmpty())
        val actor = actor(cookie)
        val requestKey = requireNotNull(key)
        require(body.get("expectedRevision")?.let { it.isIntegralNumber && it.canConvertToInt() } == true)
        AssessmentPolicy.writer(actor.role, actor.active)
        val published = publishAssessment.publish(requests.publish(actor, id, requestKey, body))
        AssessmentRepresentation.response(published)
    }

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(cookie)
}
