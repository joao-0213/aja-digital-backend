package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.GetFileMetadataQuery

fun interface GetFileMetadataInputPort {
    fun file(request: GetFileMetadataQuery): FileMetadata
}
