package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.GetMaterialDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface GetMaterialInputPort {
    fun material(request: GetMaterialDto): Map<String, Any?>
}
