package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.StoreUploadCommand
import br.com.ajadigital.core.materials.application.models.UploadFileCommand
import br.com.ajadigital.core.materials.application.models.UploadPurpose
import br.com.ajadigital.core.materials.application.ports.`in`.UploadFileInputPort
import br.com.ajadigital.core.materials.application.ports.out.FileStoreOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.materials.domain.policies.FileRules
import br.com.ajadigital.core.materials.domain.policies.MaterialRules

class UploadFileUseCase(
    private val store: FileStoreOutputPort,
) : UploadFileInputPort {
    override fun upload(request: UploadFileCommand): FileMetadata {
        val actor = request.actor
        actor.requireReader()
        require(request.key.length in MIN_IDEMPOTENCY_KEY_LENGTH..MAX_IDEMPOTENCY_KEY_LENGTH && MaterialRules.validId(request.parentId))
        require(FileRules.validFilename(request.name) && request.name != "." && request.name != "..")
        val purpose = requireNotNull(request.purpose)
        val content = request.content
        val mime = request.mime
        val name = request.name
        require(content.size <= if (purpose == UploadPurpose.FORUM) MAX_FORUM_UPLOAD_BYTES else MAX_UPLOAD_BYTES)
        when (purpose) {
            UploadPurpose.MATERIAL -> {
                require(mime.equals("application/pdf", true) && FileRules.validFileSignature(name, mime, content.header()))
                actor.requireWriter()
            }
            UploadPurpose.FORUM -> require(FileRules.validFileSignature(name, mime, content.header()))
            UploadPurpose.WORK -> if (actor.role != "student") throw MaterialsNotFound()
        }
        return store.upload(
            StoreUploadCommand(actor, request.key, name, FileRules.normalizedMime(mime), purpose, request.parentId, content),
        )
    }
}

private const val MIN_IDEMPOTENCY_KEY_LENGTH = 8
private const val MAX_IDEMPOTENCY_KEY_LENGTH = 128

private const val MAX_FORUM_UPLOAD_BYTES = 5 * 1024 * 1024

private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
