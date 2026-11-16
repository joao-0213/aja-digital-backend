package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.ports.`in`.UploadFileUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class UploadFile(private val store: ContentStore) : UploadFileUseCase {
    override fun upload(actor: AcademicActor, key: String, name: String, mime: String, purpose: String, parentId: String, bytes: ByteArray): Map<String, Any?> {
        MaterialsSupport.reader(actor)
        require(key.length in 8..128 && MaterialsSupport.validId(parentId))
        require(name.isNotBlank() && name.length <= 255 && name.none { it.isISOControl() } && '/' !in name && '\\' !in name && name != "." && name != "..")
        require(purpose in setOf("material", "work", "forum"))
        require(bytes.size <= if (purpose == "forum") 5 * 1024 * 1024 else 10 * 1024 * 1024)
        when (purpose) {
            "material" -> require(bytes.isNotEmpty() && mime.equals("application/pdf", true) && name.endsWith(".pdf", true) && bytes.copyOfRange(0, minOf(5, bytes.size)).toString(Charsets.US_ASCII) == "%PDF-")
            "forum" -> require(MaterialsSupport.validForumFile(name, mime, bytes))
        }
        return store.upload(actor.id, actor.role, actor.sessionTokenHash, key, name, MaterialsSupport.normalizedMime(mime), purpose, parentId, bytes)
    }
}
