package br.com.ajadigital.core.materials.application.ports.out

import br.com.ajadigital.core.materials.application.models.DiscardFileCommand
import br.com.ajadigital.core.materials.application.models.DownloadFileQuery
import br.com.ajadigital.core.materials.application.models.DownloadedFile
import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.GetFileMetadataQuery
import br.com.ajadigital.core.materials.application.models.StoreUploadCommand

interface FileStoreOutputPort {
    fun upload(request: StoreUploadCommand): FileMetadata

    fun file(request: GetFileMetadataQuery): FileMetadata?

    fun download(request: DownloadFileQuery): DownloadedFile?

    fun discard(request: DiscardFileCommand)
}
