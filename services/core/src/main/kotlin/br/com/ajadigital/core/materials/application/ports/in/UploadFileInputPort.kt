package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.UploadFileCommand

fun interface UploadFileInputPort {
    fun upload(request: UploadFileCommand): FileMetadata
}
