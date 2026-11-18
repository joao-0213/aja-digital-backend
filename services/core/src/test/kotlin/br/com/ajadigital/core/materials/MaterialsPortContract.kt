package br.com.ajadigital.core.materials

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.contracts.MaterialKind
import br.com.ajadigital.core.materials.application.models.*
import br.com.ajadigital.core.materials.application.ports.out.FileStoreOutputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialWriteOutputPort
import br.com.ajadigital.core.materials.application.ports.out.QuestionImageOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsConflict
import br.com.ajadigital.core.materials.domain.exceptions.InvalidMaterialEdit
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsForbidden
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.materials.domain.models.MaterialRevision
import br.com.ajadigital.core.materials.domain.models.NewMaterial
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

internal object MaterialsPortContract {
    fun stagedFile(store: FileStoreOutputPort, owner: AuthenticatedActor, other: AuthenticatedActor, moduleId: String) {
        val bytes = "%PDF-1.4\ncontract\n%%EOF".toByteArray()
        val command = StoreUploadCommand(owner, "test-file-contract", "contract.pdf", "application/pdf", UploadPurpose.MATERIAL, moduleId, FileBytes(bytes))
        val staged = store.upload(command)
        assertEquals(FileStatus.STAGED, staged.status)
        assertEquals(staged, store.upload(command))
        assertEquals(staged, store.file(GetFileMetadataQuery(owner, staged.id)))
        assertNull(store.file(GetFileMetadataQuery(other, staged.id)))
        assertThrows(MaterialsConflict::class.java) { store.discard(DiscardFileCommand(other, staged.id)) }
        val file = checkNotNull(store.download(DownloadFileQuery(owner, staged.id, FileDisposition.INLINE)))
        assertTrue(file.inline)
        assertArrayEquals(bytes, file.bytes)
        store.discard(DiscardFileCommand(owner, staged.id))
        assertNull(store.file(GetFileMetadataQuery(owner, staged.id)))
        assertThrows(MaterialsNotFound::class.java) { store.upload(command) }
    }

    fun immutableQuestionImage(store: QuestionImageOutputPort, id: String) {
        val bytes = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val original = StoreQuestionImageCommand(id, "contract-question", "question.png", "image/png", FileBytes(bytes))
        val attached = store.provisionQuestionImage(original)
        assertEquals(FileStatus.ATTACHED, attached.status)
        assertEquals(FilePurpose.QUESTION, attached.purpose)
        assertEquals(attached, store.provisionQuestionImage(original))
        val changed = original.copy(content = FileBytes(bytes + 1.toByte()))
        assertThrows(MaterialsConflict::class.java) { store.provisionQuestionImage(changed) }
        assertEquals(attached, store.provisionQuestionImage(original))
    }

    fun materialWrites(store: MaterialWriteOutputPort, actor: AuthenticatedActor, other: AuthenticatedActor, moduleId: String) {
        assertThrows(MaterialsForbidden::class.java) { store.authorizeMaterialPublication(other, moduleId) }
        store.authorizeMaterialPublication(actor, moduleId)
        val draft = NewMaterial(moduleId, "Contract lesson", 10.0, "Body", NewMaterial.Source.Lesson)
        val command = StorePublishMaterialsCommand(actor, moduleId, 1, listOf(draft))
        val created = store.publishMaterials(command).items.single()
        assertEquals(MaterialKind.LESSON, created.kind)
        assertEquals(moduleId, created.moduleId)
        assertEquals(1, created.revision)
        assertThrows(MaterialsConflict::class.java) { store.publishMaterials(command) }

        val id = created.id
        val edit = StoreUpdateMaterialCommand(actor, id, 1, "Updated", null, null, null)
        val invalid = edit.copy(videoUrl = "https://example.test/video")
        assertThrows(InvalidMaterialEdit::class.java) { store.updateMaterial(invalid) }
        val changed = store.updateMaterial(edit)
        assertEquals("Updated", changed.title)
        assertEquals(2, changed.revision)
        val staleOrder = ReorderMaterialsCommand(actor, moduleId, 3, listOf(MaterialRevision(id, 1)), 1, 20)
        assertThrows(MaterialsConflict::class.java) { store.reorderMaterials(staleOrder) }
        val correctOrder = ReorderMaterialsCommand(actor, moduleId, 3, listOf(MaterialRevision(id, 2)), 1, 20)
        assertEquals(3, store.reorderMaterials(correctOrder).items.single().revision)
    }
}
