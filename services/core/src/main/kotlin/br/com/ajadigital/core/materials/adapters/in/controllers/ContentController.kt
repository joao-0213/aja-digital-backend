package br.com.ajadigital.core.materials.adapters.`in`.controllers
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.UploadFileDto

import br.com.ajadigital.core.materials.application.models.UpdateMaterialDto

import br.com.ajadigital.core.materials.application.models.ReorderMaterialsDto

import br.com.ajadigital.core.materials.application.models.RecordMaterialProgressDto

import br.com.ajadigital.core.materials.application.models.PublishMaterialsDto

import br.com.ajadigital.core.materials.domain.models.ProvisionQuestionImageDto

import br.com.ajadigital.core.materials.application.models.ListMaterialsDto

import br.com.ajadigital.core.materials.application.models.GetMaterialDto

import br.com.ajadigital.core.materials.application.models.GetLearningProgressDto

import br.com.ajadigital.core.materials.application.models.GetFileMetadataDto

import br.com.ajadigital.core.materials.application.models.DownloadFileDto

import br.com.ajadigital.core.materials.application.models.DiscardFileDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests.MaterialBatchRequest
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests.MaterialEditRequest
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests.ProgressCommandRequest
import br.com.ajadigital.core.materials.application.ports.`in`.DiscardFileInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.DownloadFileInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.GetFileMetadataInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.GetLearningProgressInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.GetMaterialInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.ListMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.ProvisionQuestionImageInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.PublishMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.RecordMaterialProgressInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.ReorderMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.UpdateMaterialInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.UploadFileInputPort
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.OrderCommandRequest
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
import tools.jackson.databind.JsonNode
import java.nio.charset.StandardCharsets

@RestController
class ContentController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val uploadFile: UploadFileInputPort,
    private val provisionQuestionImage: ProvisionQuestionImageInputPort,
    private val getFileMetadata: GetFileMetadataInputPort,
    private val downloadFile: DownloadFileInputPort,
    private val discardFile: DiscardFileInputPort,
    private val listMaterials: ListMaterialsInputPort,
    private val getMaterial: GetMaterialInputPort,
    private val publishMaterials: PublishMaterialsInputPort,
    private val updateMaterial: UpdateMaterialInputPort,
    private val reorderMaterials: ReorderMaterialsInputPort,
    private val getLearningProgress: GetLearningProgressInputPort,
    private val recordMaterialProgress: RecordMaterialProgressInputPort,
) {
    @PostMapping("/api/v1/files", consumes = ["multipart/form-data"])
    fun upload(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader("Idempotency-Key", required = false) key: String?, request: MultipartHttpServletRequest): ResponseEntity<Any> {
        require(request.fileMap.keys == setOf("file") && request.multiFileMap.getValue("file").size == 1 && request.parameterMap.keys == setOf("purpose", "parentId") && request.parameterMap.values.all { it.size == 1 })
        val file = request.fileMap.getValue("file")
        val response = uploadFile.upload(UploadFileDto(actor(cookie), key ?: throw IllegalArgumentException(), file.originalFilename ?: throw IllegalArgumentException(), file.contentType ?: "application/octet-stream", request.getParameter("purpose"), request.getParameter("parentId"), file.bytes))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/internal/v1/question-files/{id}", consumes = ["multipart/form-data"])
    fun provisionQuestionImage(@PathVariable id: String, request: MultipartHttpServletRequest): ResponseEntity<Any> {
        require(request.fileMap.keys == setOf("file") && request.multiFileMap.getValue("file").size == 1 && request.parameterMap.keys == setOf("questionId") && request.parameterMap.values.all { it.size == 1 })
        val file = request.fileMap.getValue("file")
        return ResponseEntity.status(HttpStatus.CREATED).body(provisionQuestionImage.provisionQuestionImage(ProvisionQuestionImageDto(id, request.getParameter("questionId"), file.originalFilename ?: throw IllegalArgumentException(), file.contentType ?: throw IllegalArgumentException(), file.bytes)))
    }

    @GetMapping("/api/v1/files/{id}")
    fun file(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getFileMetadata.file(GetFileMetadataDto(actor(cookie), id))

    @GetMapping("/api/v1/files/{id}/content")
    fun download(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestParam(required = false) disposition: String?): ResponseEntity<ByteArray> {
        val file = downloadFile.download(DownloadFileDto(actor(cookie), id, disposition))
        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType(file.mime)
            contentDisposition = ContentDisposition.builder(if (file.inline) "inline" else "attachment").filename(file.name, StandardCharsets.UTF_8).build()
            set("X-Content-Type-Options", "nosniff")
        }
        return ResponseEntity.ok().headers(headers).body(file.bytes)
    }

    @PostMapping("/api/v1/files/{id}/discard")
    fun discard(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody(required = false) body: Map<String, Any?>?) = run { require(body.isNullOrEmpty()); discardFile.discard(DiscardFileDto(actor(cookie), id)); ResponseEntity.noContent().build<Void>() }

    @GetMapping("/api/v1/materials")
    fun materials(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "order") sort: String, @RequestParam(required = false) moduleId: String?, @RequestParam(required = false) subjectId: String?) = listMaterials.materials(ListMaterialsDto(actor(cookie), page, pageSize, sort, moduleId, subjectId)).let { mapOf("items" to it.items, "page" to it.page, "pageSize" to it.pageSize, "total" to it.total) }

    @GetMapping("/api/v1/materials/{id}")
    fun material(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getMaterial.material(GetMaterialDto(actor(cookie), id))

    @PostMapping("/api/v1/materials/batch")
    fun publish(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: MaterialBatchRequest): ResponseEntity<Any> {
        val revision = body.expectedRevision.intValue().also { require(body.expectedRevision.isIntegralNumber && body.expectedRevision.canConvertToInt()) }
        return ResponseEntity.status(HttpStatus.CREATED).body(publishMaterials.publish(PublishMaterialsDto(actor(cookie), key ?: throw IllegalArgumentException(), body.moduleId, revision, body.items.map { it.command() })))
    }

    @PatchMapping("/api/v1/materials/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: MaterialEditRequest) = updateMaterial.update(UpdateMaterialDto(actor(cookie), id, integer(body.expectedRevision), text(body.title), decimal(body.duration), text(body.content), text(body.videoUrl)))

    @PostMapping("/api/v1/modules/{id}/reorder-materials")
    fun reorder(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestBody body: OrderCommandRequest) = reorderMaterials.reorder(ReorderMaterialsDto(actor(cookie), id, integer(body.expectedRevision), body.items.map { it.id to it.expectedRevision }, page, pageSize))

    @GetMapping("/api/v1/me/progress")
    fun progress(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) subjectId: String?, @RequestParam(required = false) materialId: String?) = getLearningProgress.progress(GetLearningProgressDto(actor(cookie), page, pageSize, sort, subjectId, materialId))

    @PatchMapping("/api/v1/me/progress")
    fun recordProgress(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestBody body: ProgressCommandRequest) = recordMaterialProgress.recordProgress(RecordMaterialProgressDto(actor(cookie), body.materialId, if (body.completed.isMissingNode) null else body.completed.booleanValue().also { require(body.completed.isBoolean) }))

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))
    private fun integer(value: JsonNode) = value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt()) }
    private fun decimal(value: JsonNode) = if (value.isMissingNode) null else value.doubleValue().also { require(value.isNumber && it.isFinite()) }
    private fun text(value: JsonNode) = if (value.isMissingNode) null else value.asString().also { require(value.isTextual) }
}
