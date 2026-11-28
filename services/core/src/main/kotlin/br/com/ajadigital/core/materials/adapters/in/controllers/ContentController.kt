package br.com.ajadigital.core.materials.adapters.`in`.controllers
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests.MaterialBatchRequest
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests.MaterialEditRequest
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests.ProgressCommandRequest
import br.com.ajadigital.core.materials.adapters.`in`.controllers.models.responses.toResponse
import br.com.ajadigital.core.materials.application.models.DiscardFileCommand
import br.com.ajadigital.core.materials.application.models.DownloadFileQuery
import br.com.ajadigital.core.materials.application.models.FileDisposition
import br.com.ajadigital.core.materials.application.models.GetFileMetadataQuery
import br.com.ajadigital.core.materials.application.models.GetLearningProgressQuery
import br.com.ajadigital.core.materials.application.models.GetMaterialQuery
import br.com.ajadigital.core.materials.application.models.ListMaterialsQuery
import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.MaterialSort
import br.com.ajadigital.core.materials.application.models.ProgressSort
import br.com.ajadigital.core.materials.application.models.ProvisionQuestionImageCommand
import br.com.ajadigital.core.materials.application.models.PublishMaterialsCommand
import br.com.ajadigital.core.materials.application.models.QuestionServiceCredential
import br.com.ajadigital.core.materials.application.models.RecordMaterialProgressCommand
import br.com.ajadigital.core.materials.application.models.ReorderMaterialsCommand
import br.com.ajadigital.core.materials.application.models.UpdateMaterialCommand
import br.com.ajadigital.core.materials.application.models.UploadFileCommand
import br.com.ajadigital.core.materials.application.models.UploadPurpose
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
import br.com.ajadigital.core.materials.domain.models.MaterialRevision
import br.com.ajadigital.core.materials.domain.models.ProgressAction
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.OrderCommandRequest
import br.com.ajadigital.core.shared.application.contracts.FieldChange
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
        val response = uploadFile.upload(UploadFileCommand(actor(cookie), key ?: throw IllegalArgumentException(), file.originalFilename ?: throw IllegalArgumentException(), file.contentType ?: "application/octet-stream", UploadPurpose.parse(request.getParameter("purpose")), request.getParameter("parentId"), file.bytes))
        return ResponseEntity.status(HttpStatus.CREATED).body(response.toResponse())
    }

    @PostMapping("/internal/v1/question-files/{id}", consumes = ["multipart/form-data"])
    fun provisionQuestionImage(@PathVariable id: String, request: MultipartHttpServletRequest): ResponseEntity<Any> {
        require(request.fileMap.keys == setOf("file") && request.multiFileMap.getValue("file").size == 1 && request.parameterMap.keys == setOf("questionId") && request.parameterMap.values.all { it.size == 1 })
        val file = request.fileMap.getValue("file")
        val response = provisionQuestionImage.provisionQuestionImage(
            ProvisionQuestionImageCommand(
                id,
                request.getParameter("questionId"),
                file.originalFilename ?: throw IllegalArgumentException(),
                file.contentType ?: throw IllegalArgumentException(),
                file.bytes,
                QuestionServiceCredential(request.getHeader("X-AJA-Service-Token")),
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response.toResponse())
    }

    @GetMapping("/api/v1/files/{id}")
    fun file(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getFileMetadata.file(GetFileMetadataQuery(actor(cookie), id)).toResponse()

    @GetMapping("/api/v1/files/{id}/content")
    fun download(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestParam(required = false) disposition: String?): ResponseEntity<ByteArray> {
        val actor = actor(cookie)
        val mode = disposition?.let { requireNotNull(FileDisposition.parse(it)) }
        val file = downloadFile.download(DownloadFileQuery(actor, id, mode))
        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType(file.mime)
            contentDisposition = ContentDisposition.builder(if (file.inline) "inline" else "attachment").filename(file.name, StandardCharsets.UTF_8).build()
            set("X-Content-Type-Options", "nosniff")
        }
        return ResponseEntity.ok().headers(headers).body(file.bytes)
    }

    @PostMapping("/api/v1/files/{id}/discard")
    fun discard(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody(required = false) body: Map<String, Any?>?) = run {
        require(body.isNullOrEmpty())
        discardFile.discard(DiscardFileCommand(actor(cookie), id))
        ResponseEntity.noContent().build<Void>()
    }

    @GetMapping("/api/v1/materials")
    fun materials(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "order") sort: String, @RequestParam(required = false) moduleId: String?, @RequestParam(required = false) subjectId: String?) = listMaterials.materials(ListMaterialsQuery(actor(cookie), page, pageSize, MaterialSort.parse(sort), moduleId, subjectId)).toResponse(Material::toResponse)

    @GetMapping("/api/v1/materials/{id}")
    fun material(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getMaterial.material(GetMaterialQuery(actor(cookie), id)).toResponse()

    @PostMapping("/api/v1/materials/batch")
    fun publish(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: MaterialBatchRequest): ResponseEntity<Any> {
        val revision = body.expectedRevision.intValue().also { require(body.expectedRevision.isIntegralNumber && body.expectedRevision.canConvertToInt()) }
        return ResponseEntity.status(HttpStatus.CREATED).body(publishMaterials.publish(PublishMaterialsCommand(actor(cookie), key ?: throw IllegalArgumentException(), body.moduleId, revision, body.items.map { it.command() })).toResponse(Material::toResponse))
    }

    @PatchMapping("/api/v1/materials/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: MaterialEditRequest) = updateMaterial.update(UpdateMaterialCommand(actor(cookie), id, integer(body.expectedRevision), text(body.title), decimal(body.duration), text(body.content), text(body.videoUrl))).toResponse()

    @PostMapping("/api/v1/modules/{id}/reorder-materials")
    fun reorder(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestBody body: OrderCommandRequest) = reorderMaterials.reorder(ReorderMaterialsCommand(actor(cookie), id, integer(body.expectedRevision), body.items.map { MaterialRevision(it.id, it.expectedRevision) }, page, pageSize)).toResponse(Material::toResponse)

    @GetMapping("/api/v1/me/progress")
    fun progress(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) subjectId: String?, @RequestParam(required = false) materialId: String?) = getLearningProgress.progress(GetLearningProgressQuery(actor(cookie), page, pageSize, ProgressSort.parse(sort), subjectId, materialId)).toResponse { it }

    @PatchMapping("/api/v1/me/progress")
    fun recordProgress(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestBody body: ProgressCommandRequest) = recordMaterialProgress.recordProgress(RecordMaterialProgressCommand(actor(cookie), body.materialId, progressAction(body.completed)))

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(cookie)
    private fun integer(value: JsonNode) = value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt()) }
    private fun decimal(value: JsonNode): FieldChange<Double> = if (value.isMissingNode) {
        FieldChange.Unchanged
    } else {
        FieldChange.Set(value.doubleValue().also { require(value.isNumber && it.isFinite()) })
    }
    private fun text(value: JsonNode): FieldChange<String> = if (value.isMissingNode) {
        FieldChange.Unchanged
    } else {
        FieldChange.Set(value.asString().also { require(value.isString) })
    }

    private fun progressAction(value: JsonNode): ProgressAction? {
        if (value.isMissingNode) return ProgressAction.RECORD_ACCESS
        require(value.isBoolean)
        return if (value.booleanValue()) ProgressAction.COMPLETE else null
    }
}
