package br.com.ajadigital.core

import br.com.ajadigital.core.application.academic.AcademicActor
import br.com.ajadigital.core.application.academic.ManageContent
import br.com.ajadigital.core.application.identity.AuthenticateStudent
import br.com.ajadigital.core.application.identity.SessionRequired
import br.com.ajadigital.core.ports.out.MaterialCreate
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
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
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class FileUploadSessionFilter(private val auth: AuthenticateStudent, @Value("\${core.internal.questions-token}") private val questionsToken: String) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.method == "POST" && request.requestURI.startsWith("/internal/v1/question-files/")) {
            val supplied = request.getHeader("X-AJA-Service-Token") ?: ""
            if (!MessageDigest.isEqual(questionsToken.toByteArray(), supplied.toByteArray())) {
                response.status = HttpServletResponse.SC_FORBIDDEN
                response.contentType = "application/json"
                response.writer.write("""{"error":{"code":"FORBIDDEN","message":"Service credential is invalid","requestId":"${UUID.randomUUID()}"}}""")
                return
            }
            return chain.doFilter(request, response)
        }
        if (request.method != "POST" || request.requestURI != "/api/v1/files") return chain.doFilter(request, response)
        val cookie = request.cookies?.firstOrNull { it.name == "aja_session" }?.value
        try { auth.resolve(cookie) } catch (_: SessionRequired) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error":{"code":"SESSION_REQUIRED","message":"A valid session is required","requestId":"${UUID.randomUUID()}"}}""")
            return
        }
        chain.doFilter(request, response)
    }
}

@JsonIgnoreProperties(ignoreUnknown = false)
data class MaterialBatchRequest(val moduleId: String, val expectedRevision: JsonNode = MissingNode.getInstance(), val items: List<MaterialCreateRequest>)
@JsonIgnoreProperties(ignoreUnknown = false)
data class MaterialCreateRequest(val moduleId: String, val title: String, val kind: String, val duration: Double, val content: String, val fileId: String? = null, val videoUrl: String? = null) {
    fun command() = MaterialCreate(moduleId, title, kind, duration, content, fileId, videoUrl)
}
@JsonIgnoreProperties(ignoreUnknown = false)
data class MaterialEditRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val title: JsonNode = MissingNode.getInstance(), val duration: JsonNode = MissingNode.getInstance(), val content: JsonNode = MissingNode.getInstance(), val videoUrl: JsonNode = MissingNode.getInstance())
@JsonIgnoreProperties(ignoreUnknown = false)
data class ProgressCommandRequest(val materialId: String, val completed: JsonNode = MissingNode.getInstance())

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
