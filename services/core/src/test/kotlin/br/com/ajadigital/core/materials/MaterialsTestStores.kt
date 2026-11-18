package br.com.ajadigital.core.materials

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.contracts.MaterialKind
import br.com.ajadigital.core.materials.application.models.*
import br.com.ajadigital.core.materials.application.ports.out.*
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsConflict
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsForbidden
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsIdempotencyConflict
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.materials.domain.models.FilePreparation
import br.com.ajadigital.core.materials.domain.models.MaterialRevision
import br.com.ajadigital.core.materials.domain.models.NewMaterial
import br.com.ajadigital.core.materials.domain.models.QuestionImageIdentity
import br.com.ajadigital.core.materials.domain.policies.MaterialRules
import br.com.ajadigital.core.shared.application.contracts.Page
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

/** One prepared material file and its durable upload receipt. */
internal class TestStagedFileStore(private val allowedOwner: String, private val moduleId: String, private val clock: Clock) : FileStoreOutputPort {
    private var recordedBytes: ByteArray? = null
    private var preparation: FilePreparation? = null
    private var metadata: FileMetadata? = null

    override fun upload(request: StoreUploadCommand): FileMetadata {
        if (request.actor.id != allowedOwner) throw MaterialsForbidden()
        if (request.parentId != moduleId) throw MaterialsNotFound()
        check(request.purpose == UploadPurpose.MATERIAL)
        recordedBytes?.let {
            if (!it.contentEquals(request.content.copy())) throw MaterialsIdempotencyConflict()
            return file(GetFileMetadataQuery(request.actor, "staged-file")) ?: throw MaterialsNotFound()
        }
        val bytes = request.content.copy()
        val staged = FileMetadata("staged-file", request.name, bytes.size.toLong(), request.mime, FilePurpose.MATERIAL, moduleId, FileStatus.STAGED)
        recordedBytes = bytes
        metadata = staged
        preparation = FilePreparation(request.actor.id, clock.instant().plusSeconds(86400))
        return staged
    }

    override fun file(request: GetFileMetadataQuery): FileMetadata? {
        val staged = metadata ?: return null
        return staged.takeIf { it.id == request.id && preparation?.isOwnedBy(request.actor.id, clock.instant()) == true }
    }

    override fun download(request: DownloadFileQuery): DownloadedFile? {
        val staged = file(GetFileMetadataQuery(request.actor, request.id)) ?: return null
        return DownloadedFile(staged.name, staged.mime, checkNotNull(recordedBytes), request.disposition != FileDisposition.ATTACHMENT)
    }

    override fun discard(request: DiscardFileCommand) {
        if (metadata?.id != request.id) throw MaterialsNotFound()
        if (preparation?.isOwnedBy(request.actor.id, clock.instant()) != true) throw MaterialsConflict()
        preparation = null
        metadata = null
    }
}

internal class TestQuestionImageStore : QuestionImageOutputPort {
    private val images = mutableMapOf<String, Pair<QuestionImageIdentity, FileMetadata>>()

    override fun provisionQuestionImage(request: StoreQuestionImageCommand): FileMetadata {
        val bytes = request.content.copy()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val identity = QuestionImageIdentity(request.questionId, request.name, request.mime, hash)
        images[request.id]?.let { (prior, metadata) ->
            identity.verifyReplay(prior)
            return metadata
        }
        val metadata = FileMetadata(request.id, request.name, bytes.size.toLong(), request.mime, FilePurpose.QUESTION, request.questionId, FileStatus.ATTACHED)
        images[request.id] = identity to metadata
        return metadata
    }
}

/** Test fixture for one lesson in one module. It models revision conflicts and delegates decisions to the domain. */
internal class TestLessonStore(private val teacher: String, private val moduleId: String, private val now: Instant) : MaterialWriteOutputPort {
    private var moduleRevision = 1
    private var material: Material? = null

    override fun authorizeMaterialPublication(actor: AuthenticatedActor, moduleId: String) {
        if (actor.id != teacher || !actor.active || actor.role != "teacher") throw MaterialsForbidden()
        if (moduleId != this.moduleId) throw MaterialsNotFound()
    }

    override fun publishMaterials(request: StorePublishMaterialsCommand): Page<Material> {
        authorizeMaterialPublication(request.actor, request.moduleId)
        MaterialRules.requireCurrentRevision(moduleRevision, request.expectedRevision)
        val item = request.items.single()
        check(item.source == NewMaterial.Source.Lesson)
        val created = Material(
            "contract-material", 1, now.toString(), now.toString(), request.actor.id, moduleId,
            item.title.trim(), MaterialKind.LESSON, item.duration, item.content, null, null, null,
            NamedPerson(request.actor.id, "Contract teacher"), 0,
        )
        moduleRevision++
        material = created
        return Page(listOf(created), 1, 1, 1)
    }

    override fun updateMaterial(request: StoreUpdateMaterialCommand): Material {
        authorizeMaterialPublication(request.actor, moduleId)
        val current = material?.takeIf { it.id == request.id } ?: throw MaterialsConflict()
        MaterialRules.requireCurrentRevision(current.revision, request.expectedRevision)
        MaterialRules.requireVideoEdit(false, request.videoUrl)
        moduleRevision++
        return current.copy(
            title = request.title ?: current.title, duration = request.duration ?: current.duration,
            content = request.content ?: current.content, revision = current.revision + 1,
        ).also { material = it }
    }

    override fun reorderMaterials(request: ReorderMaterialsCommand): Page<Material> {
        authorizeMaterialPublication(request.actor, request.moduleId)
        MaterialRules.requireCurrentRevision(moduleRevision, request.expectedRevision)
        val current = material ?: throw MaterialsConflict()
        if (!MaterialRules.matchesCurrentOrder(request.items, listOf(MaterialRevision(current.id, current.revision)))) throw MaterialsConflict()
        val changed = current.copy(revision = current.revision + 1)
        moduleRevision++
        material = changed
        return Page(listOf(changed), request.page, request.size, 1)
    }
}
