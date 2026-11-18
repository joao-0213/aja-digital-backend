package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.UploadFileDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface UploadFileInputPort {
    fun upload(request: UploadFileDto): Map<String, Any?>
}
