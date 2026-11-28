package br.com.ajadigital.core.materials.adapters.out.serialization

import br.com.ajadigital.core.materials.application.contracts.MaterialKind
import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.FilePurpose
import br.com.ajadigital.core.materials.application.models.FileStatus
import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.NamedPerson
import br.com.ajadigital.core.materials.application.models.StorePublishMaterialsCommand
import br.com.ajadigital.core.materials.application.ports.out.MaterialPublicationReceiptOutputPort
import br.com.ajadigital.core.materials.domain.models.NewMaterial
import br.com.ajadigital.core.shared.application.contracts.Page
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

/** Codec for the original publishMaterials fingerprint and the persisted response format. */
@Component
class JacksonMaterialPublicationReceipt(
    private val mapper: ObjectMapper,
) : MaterialPublicationReceiptOutputPort {
    override fun fingerprint(request: StorePublishMaterialsCommand): String {
        val body =
            mapOf(
                "moduleId" to request.moduleId,
                "expectedRevision" to request.expectedRevision,
                "items" to
                    request.items.map { item ->
                        val source = item.source
                        mapOf(
                            "moduleId" to item.moduleId,
                            "title" to item.title,
                            "kind" to
                                when (source) {
                                    NewMaterial.Source.Lesson -> MaterialKind.LESSON.value
                                    is NewMaterial.Source.Pdf -> MaterialKind.PDF.value
                                    is NewMaterial.Source.Video -> MaterialKind.VIDEO.value
                                },
                            "duration" to item.duration,
                            "content" to item.content,
                            "fileId" to (source as? NewMaterial.Source.Pdf)?.fileId,
                            "videoUrl" to (source as? NewMaterial.Source.Video)?.url,
                        )
                    },
            )
        return MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }
    }

    override fun encode(result: Page<Material>): String = mapper.writeValueAsString(
        PublicationReceipt(result.items.map(::materialReceipt), result.page, result.pageSize, result.total),
    )

    override fun decode(response: String): Page<Material> {
        val receipt = mapper.readValue(response, PublicationReceipt::class.java)
        return Page(receipt.items.map { it.material() }, receipt.page, receipt.pageSize, receipt.total)
    }

    private fun materialReceipt(item: Material) = MaterialReceipt(
        item.id,
        item.revision,
        item.createdAt,
        item.updatedAt,
        item.updatedBy,
        item.moduleId,
        item.title,
        item.kind.value,
        item.duration,
        item.content,
        item.fileId,
        item.videoUrl,
        item.file?.let {
            FileReceipt(
                it.id,
                it.name,
                it.size,
                it.mime,
                it.purpose.publicValue,
                it.parentId,
                it.status.value,
                "/api/v1/files/${it.id}/content",
            )
        },
        PersonReceipt(item.author.id, item.author.name),
        item.order,
    )

    @JsonPropertyOrder("items", "page", "pageSize", "total")
    private data class PublicationReceipt(
        val items: List<MaterialReceipt>,
        val page: Int,
        val pageSize: Int,
        val total: Int,
    )

    @JsonPropertyOrder(
        "id",
        "revision",
        "createdAt",
        "updatedAt",
        "updatedBy",
        "moduleId",
        "title",
        "kind",
        "duration",
        "content",
        "fileId",
        "videoUrl",
        "file",
        "author",
        "order",
    )
    private data class MaterialReceipt(
        val id: String,
        val revision: Int,
        val createdAt: String,
        val updatedAt: String,
        val updatedBy: String,
        val moduleId: String,
        val title: String,
        val kind: String,
        val duration: Double,
        val content: String,
        val fileId: String?,
        val videoUrl: String?,
        val file: FileReceipt?,
        val author: PersonReceipt,
        val order: Int,
    ) {
        fun material() = Material(
            id,
            revision,
            createdAt,
            updatedAt,
            updatedBy,
            moduleId,
            title,
            checkNotNull(MaterialKind.parse(kind)),
            duration,
            content,
            fileId,
            videoUrl,
            file?.metadata(),
            NamedPerson(author.id, author.name),
            order,
        )
    }

    @JsonPropertyOrder("id", "name", "size", "mime", "purpose", "parentId", "status", "downloadUrl")
    private data class FileReceipt(
        val id: String,
        val name: String,
        val size: Long,
        val mime: String,
        val purpose: String,
        val parentId: String,
        val status: String,
        val downloadUrl: String,
    ) {
        fun metadata() = FileMetadata(
            id,
            name,
            size,
            mime,
            FilePurpose.entries.first { it.value == purpose },
            parentId,
            FileStatus.entries.first { it.value == status },
        )
    }

    @JsonPropertyOrder("id", "name")
    private data class PersonReceipt(
        val id: String,
        val name: String,
    )
}
