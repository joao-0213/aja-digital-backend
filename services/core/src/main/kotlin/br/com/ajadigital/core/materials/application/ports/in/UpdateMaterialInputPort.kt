package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.UpdateMaterialCommand

fun interface UpdateMaterialInputPort {
    fun update(request: UpdateMaterialCommand): Material
}
