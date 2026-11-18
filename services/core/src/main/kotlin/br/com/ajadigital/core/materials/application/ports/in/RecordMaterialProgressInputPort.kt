package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.RecordMaterialProgressDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface RecordMaterialProgressInputPort {
    fun recordProgress(request: RecordMaterialProgressDto): Map<String, Any?>
}
