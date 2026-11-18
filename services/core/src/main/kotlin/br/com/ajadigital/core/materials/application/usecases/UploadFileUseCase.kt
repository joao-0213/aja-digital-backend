package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ContentStoreUploadDto

import br.com.ajadigital.core.materials.application.models.UploadFileDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.ports.`in`.UploadFileInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class UploadFileUseCase(private val store: ContentStoreOutputPort) : UploadFileInputPort {
    override fun upload(request: UploadFileDto): Map<String, Any?> {
                val (actor, key, name, mime, purpose, parentId, bytes) = request
        MaterialsPolicy.reader(actor)
        require(key.length in 8..128 && MaterialsPolicy.validId(parentId))
        require(name.isNotBlank() && name.length <= 255 && name.none { it.isISOControl() } && '/' !in name && '\\' !in name && name != "." && name != "..")
        require(purpose in setOf("material", "work", "forum"))
        require(bytes.size <= if (purpose == "forum") 5 * 1024 * 1024 else 10 * 1024 * 1024)
        when (purpose) {
            "material" -> require(bytes.isNotEmpty() && mime.equals("application/pdf", true) && name.endsWith(".pdf", true) && bytes.copyOfRange(0, minOf(5, bytes.size)).toString(Charsets.US_ASCII) == "%PDF-")
            "forum" -> require(MaterialsPolicy.validForumFile(name, mime, bytes))
        }
        return store.upload(ContentStoreUploadDto(actor.id, actor.role, actor.sessionValidationReference, key, name, MaterialsPolicy.normalizedMime(mime), purpose, parentId, bytes))
    }
}
