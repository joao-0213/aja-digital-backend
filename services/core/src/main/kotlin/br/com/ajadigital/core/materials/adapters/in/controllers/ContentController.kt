package br.com.ajadigital.core.materials.adapters.`in`.controllers
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests.MaterialBatchRequest
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests.MaterialEditRequest
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.OrderCommandRequest
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests.ProgressCommandRequest

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.usecases.ManageContent
import br.com.ajadigital.core.identity.domain.usecases.AuthenticateStudent
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.nio.charset.StandardCharsets


@RestController
class ContentController(private val auth: AuthenticateStudent, private val content: ManageContent) {
    @PostMapping("/api/v1/files", consumes = ["multipart/form-data"])
    fun upload(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader("Idempotency-Key", required = false) key: String?, request: MultipartHttpServletRequest): ResponseEntity<Any> {
        require(request.fileMap.keys == setOf("file") && request.multiFileMap.getValue("file").size == 1 && request.parameterMap.keys == setOf("purpose", "parentId") && request.parameterMap.values.all { it.size == 1 })
        val file = request.fileMap.getValue("file")
        val response = content.upload(actor(cookie), key ?: throw IllegalArgumentException(), file.originalFilename ?: throw IllegalArgumentException(), file.contentType ?: "application/octet-stream", request.getParameter("purpose"), request.getParameter("parentId"), file.bytes)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/internal/v1/question-files/{id}", consumes = ["multipart/form-data"])
    fun provisionQuestionImage(@PathVariable id: String, request: MultipartHttpServletRequest): ResponseEntity<Any> {
        require(request.fileMap.keys == setOf("file") && request.multiFileMap.getValue("file").size == 1 && request.parameterMap.keys == setOf("questionId") && request.parameterMap.values.all { it.size == 1 })
        val file = request.fileMap.getValue("file")
        return ResponseEntity.status(HttpStatus.CREATED).body(content.provisionQuestionImage(id, request.getParameter("questionId"), file.originalFilename ?: throw IllegalArgumentException(), file.contentType ?: throw IllegalArgumentException(), file.bytes))
    }

    @GetMapping("/api/v1/files/{id}")
    fun file(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = content.file(actor(cookie), id)

    @GetMapping("/api/v1/files/{id}/content")
    fun download(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestParam(required = false) disposition: String?): ResponseEntity<ByteArray> {
        val file = content.download(actor(cookie), id, disposition)
        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType(file.mime)
            contentDisposition = ContentDisposition.builder(if (file.inline) "inline" else "attachment").filename(file.name, StandardCharsets.UTF_8).build()
            set("X-Content-Type-Options", "nosniff")
        }
        return ResponseEntity.ok().headers(headers).body(file.bytes)
    }

    @PostMapping("/api/v1/files/{id}/discard")
    fun discard(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody(required = false) body: Map<String, Any?>?) = run { require(body.isNullOrEmpty()); content.discard(actor(cookie), id); ResponseEntity.noContent().build<Void>() }

    @GetMapping("/api/v1/materials")
    fun materials(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "order") sort: String, @RequestParam(required = false) moduleId: String?, @RequestParam(required = false) subjectId: String?) = content.materials(actor(cookie), page, pageSize, sort, moduleId, subjectId).let { mapOf("items" to it.items, "page" to it.page, "pageSize" to it.pageSize, "total" to it.total) }

    @GetMapping("/api/v1/materials/{id}")
    fun material(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = content.material(actor(cookie), id)

    @PostMapping("/api/v1/materials/batch")
    @Transactional
    fun publish(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: MaterialBatchRequest): ResponseEntity<Any> {
        val revision = body.expectedRevision.intValue().also { require(body.expectedRevision.isIntegralNumber && body.expectedRevision.canConvertToInt()) }
        return ResponseEntity.status(HttpStatus.CREATED).body(content.publish(actor(cookie), key ?: throw IllegalArgumentException(), body.moduleId, revision, body.items.map { it.command() }))
    }

    @PatchMapping("/api/v1/materials/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: MaterialEditRequest) = content.update(actor(cookie), id, integer(body.expectedRevision), text(body.title), decimal(body.duration), text(body.content), text(body.videoUrl))

    @PostMapping("/api/v1/modules/{id}/reorder-materials")
    fun reorder(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestBody body: OrderCommandRequest) = content.reorder(actor(cookie), id, integer(body.expectedRevision), body.items.map { it.id to it.expectedRevision }, page, pageSize)

    @GetMapping("/api/v1/me/progress")
    fun progress(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) subjectId: String?, @RequestParam(required = false) materialId: String?) = content.progress(actor(cookie), page, pageSize, sort, subjectId, materialId)

    @PatchMapping("/api/v1/me/progress")
    fun recordProgress(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestBody body: ProgressCommandRequest) = content.recordProgress(actor(cookie), body.materialId, if (body.completed.isMissingNode) null else body.completed.booleanValue().also { require(body.completed.isBoolean) })

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
    private fun integer(value: JsonNode) = value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt()) }
    private fun decimal(value: JsonNode) = if (value.isMissingNode) null else value.doubleValue().also { require(value.isNumber && it.isFinite()) }
    private fun text(value: JsonNode) = if (value.isMissingNode) null else value.asString().also { require(value.isTextual) }
}
