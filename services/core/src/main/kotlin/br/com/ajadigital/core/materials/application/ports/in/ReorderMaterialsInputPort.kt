package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ReorderMaterialsDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface ReorderMaterialsInputPort {
    fun reorder(request: ReorderMaterialsDto): Map<String, Any?>
}
