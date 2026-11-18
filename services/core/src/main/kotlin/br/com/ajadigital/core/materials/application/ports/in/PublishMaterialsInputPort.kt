package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.PublishMaterialsDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.models.MaterialCreate

interface PublishMaterialsInputPort {
    fun publish(request: PublishMaterialsDto): Map<String, Any?>
}
