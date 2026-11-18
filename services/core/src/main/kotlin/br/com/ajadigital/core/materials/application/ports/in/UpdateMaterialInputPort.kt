package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.UpdateMaterialDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface UpdateMaterialInputPort {
    fun update(request: UpdateMaterialDto): Map<String, Any?>
}
