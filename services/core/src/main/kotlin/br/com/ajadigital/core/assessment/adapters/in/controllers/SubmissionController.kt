package br.com.ajadigital.core.assessment.adapters.`in`.controllers

import br.com.ajadigital.core.assessment.application.models.CreateSubmissionCommand
import br.com.ajadigital.core.assessment.application.models.GetSubmissionQuery
import br.com.ajadigital.core.assessment.application.models.ListSubmissionsQuery
import br.com.ajadigital.core.assessment.application.models.PublishSubmissionFeedbackCommand
import br.com.ajadigital.core.assessment.application.models.SaveSubmissionFeedbackCommand
import br.com.ajadigital.core.assessment.application.models.SubmissionContracts
import br.com.ajadigital.core.assessment.application.models.SubmitSubmissionCommand
import br.com.ajadigital.core.assessment.application.models.UpdateSubmissionCommand
import br.com.ajadigital.core.assessment.application.ports.`in`.CreateSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.GetSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListSubmissionsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SaveSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SubmitSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateSubmissionInputPort
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.toRequestValue
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
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

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
    private val mapper: ObjectMapper,
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
        val result = listSubmissions.submissions(ListSubmissionsQuery(actor(cookie), page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, assessmentId, studentId))
        return mapOf("items" to result.items.map { it.toWireMap() }, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }

    @PostMapping("/api/v1/submissions")
    fun create(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Any> {
        require(request.parameterMap.isEmpty())
        return ResponseEntity.status(HttpStatus.CREATED).body(createSubmission.create(CreateSubmissionCommand(actor(cookie), key ?: throw IllegalArgumentException(), createBody(body), requestHash(body))).toWireMap())
    }

    @GetMapping("/api/v1/submissions/{id}")
    fun submission(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return getSubmission.submission(GetSubmissionQuery(actor(cookie), id)).toWireMap()
    }

    @PatchMapping("/api/v1/submissions/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return updateSubmission.update(UpdateSubmissionCommand(actor(cookie), id, updateBody(body))).toWireMap()
    }

    @PostMapping("/api/v1/submissions/{id}/submit")
    fun submit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return submitSubmission.submit(SubmitSubmissionCommand(actor(cookie), id, key ?: throw IllegalArgumentException(), submitBody(body), requestHash(body))).toWireMap()
    }

    @PatchMapping("/api/v1/submissions/{id}/feedback")
    fun saveFeedback(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return saveSubmissionFeedback.saveFeedback(SaveSubmissionFeedbackCommand(actor(cookie), id, feedbackBody(body))).toWireMap()
    }

    @PostMapping("/api/v1/submissions/{id}/publish-feedback")
    fun publishFeedback(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: JsonNode, request: HttpServletRequest): Map<String, Any?> {
        require(request.parameterMap.isEmpty())
        return publishSubmissionFeedback.publishFeedback(PublishSubmissionFeedbackCommand(actor(cookie), id, key ?: throw IllegalArgumentException(), publishFeedbackBody(body), requestHash(body))).toWireMap()
    }

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(cookie)
    private fun requestHash(body: JsonNode) = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body.toRequestValue())).joinToString("") { "%02x".format(it) }

    private fun createBody(node: JsonNode): SubmissionContracts.CreateBody {
        val (attachments, attachmentsValid) = attachmentBodies(node.get("attachments"))
        val assessmentId = text(node.get("assessmentId"))
        return SubmissionContracts.CreateBody(assessmentId, attachments, shapeValid(node, setOf("assessmentId", "attachments")) && assessmentId != null && attachmentsValid)
    }

    private fun updateBody(node: JsonNode): SubmissionContracts.UpdateBody {
        val present = node.isObject && node.has("attachments")
        val (attachments, attachmentsValid) = if (present) attachmentBodies(node.get("attachments")) else null to true
        val revision = integer(node.get("expectedRevision"))
        return SubmissionContracts.UpdateBody(revision, present, attachments, shapeValid(node, setOf("expectedRevision", "attachments"), setOf("expectedRevision")) && revision != null && attachmentsValid)
    }

    private fun submitBody(node: JsonNode): SubmissionContracts.SubmitBody {
        val revision = integer(node.get("expectedRevision"))
        return SubmissionContracts.SubmitBody(revision, shapeValid(node, setOf("expectedRevision")) && revision != null)
    }

    private fun feedbackBody(node: JsonNode): SubmissionContracts.SaveFeedbackBody {
        val revision = integer(node.get("expectedRevision"))
        val comment = text(node.get("comment"))
        val gradeNode = node.get("grade")
        val grade = gradeNode?.takeIf { it.isNumber }?.numberValue()?.toString()?.toBigDecimalOrNull()
        val gradeValid = gradeNode?.isNull == true || grade != null
        return SubmissionContracts.SaveFeedbackBody(
            revision,
            comment,
            grade,
            shapeValid(node, setOf("expectedRevision", "comment", "grade")) && revision != null && comment != null && gradeValid,
        )
    }

    private fun publishFeedbackBody(node: JsonNode): SubmissionContracts.PublishFeedbackBody {
        val revision = integer(node.get("expectedRevision"))
        return SubmissionContracts.PublishFeedbackBody(revision, shapeValid(node, setOf("expectedRevision")) && revision != null)
    }

    private fun attachmentBodies(node: JsonNode?): Pair<List<SubmissionContracts.AttachmentBody>?, Boolean> {
        if (node?.isArray != true) return null to false
        val values = List(node.size()) { index ->
            val item = node.get(index)
            val kind = text(item.get("kind"))
            val name = text(item.get("name"))
            val fileId = text(item.get("fileId"))
            val url = text(item.get("url"))
            val expected = when (kind) {
                "file" -> setOf("kind", "fileId", "name")
                "link" -> setOf("kind", "name", "url")
                else -> emptySet()
            }
            val valid = item.isObject && expected.isNotEmpty() && fields(item) == expected && kind != null && name != null &&
                (kind != "file" || fileId != null) && (kind != "link" || url != null)
            SubmissionContracts.AttachmentBody(kind, name, fileId, url, valid)
        }
        return values to values.all { it.fieldsValid }
    }

    private fun text(node: JsonNode?) = node?.takeIf { it.isTextual }?.stringValue()
    private fun integer(node: JsonNode?) = when (val value = runCatching { node?.numberValue() }.getOrNull()) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        else -> null
    }
    private fun shapeValid(node: JsonNode, allowed: Set<String>, required: Set<String> = allowed): Boolean = node.isObject && fields(node).all { it in allowed } && required.all(node::has)
    private fun fields(node: JsonNode) = if (node.isObject) node.propertyNames().asSequence().toSet() else emptySet()

    private fun br.com.ajadigital.core.assessment.domain.models.SubmissionView.toWireMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "id" to id, "revision" to revision, "createdAt" to createdAt, "updatedAt" to updatedAt, "updatedBy" to updatedBy,
        "assessmentId" to assessmentId, "userId" to userId,
        "attachments" to attachments.map { attachment ->
            if (attachment.kind == "file") {
                linkedMapOf("kind" to attachment.kind, "name" to attachment.name, "fileId" to attachment.fileId)
            } else {
                linkedMapOf("url" to attachment.url, "kind" to attachment.kind, "name" to attachment.name)
            }
        },
        "submittedAt" to submittedAt, "receipt" to receipt?.let { mapOf("id" to it.id, "resourceId" to it.resourceId, "submittedAt" to it.submittedAt) },
        "feedbackPublished" to feedbackPublished?.toWireMap(), "feedbackUpdated" to feedbackUpdated,
    ).also { result ->
        if (this is br.com.ajadigital.core.assessment.domain.models.SubmissionView.StaffSubmitted) {
            result["studentName"] = studentName
            result["feedbackDraft"] = feedbackDraft?.toWireMap()
            result["feedbackHistory"] = feedbackHistory.map { it.toWireMap() }
        }
    }

    private fun br.com.ajadigital.core.assessment.domain.models.SubmissionView.Feedback.toWireMap() = linkedMapOf(
        "grade" to grade?.toNumber(),
        "author" to linkedMapOf("id" to author.id, "name" to author.name),
        "comment" to comment,
        "createdAt" to createdAt,
    )
}
