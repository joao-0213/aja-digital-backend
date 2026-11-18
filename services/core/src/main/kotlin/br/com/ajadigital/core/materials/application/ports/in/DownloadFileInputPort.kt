package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.DownloadFileDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.models.DownloadedFile

interface DownloadFileInputPort {
    fun download(request: DownloadFileDto): DownloadedFile
}
