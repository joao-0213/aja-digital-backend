package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.GetFileMetadataDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface GetFileMetadataInputPort {
    fun file(request: GetFileMetadataDto): Map<String, Any?>
}
