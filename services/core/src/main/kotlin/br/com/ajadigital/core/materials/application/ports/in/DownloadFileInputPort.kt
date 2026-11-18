package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.DownloadFileQuery
import br.com.ajadigital.core.materials.application.models.DownloadedFile

fun interface DownloadFileInputPort {
    fun download(request: DownloadFileQuery): DownloadedFile
}
