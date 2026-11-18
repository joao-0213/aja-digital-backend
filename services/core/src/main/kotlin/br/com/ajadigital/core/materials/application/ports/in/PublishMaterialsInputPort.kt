package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.PublishMaterialsCommand
import br.com.ajadigital.core.shared.application.contracts.Page

fun interface PublishMaterialsInputPort {
    fun publish(request: PublishMaterialsCommand): Page<Material>
}
