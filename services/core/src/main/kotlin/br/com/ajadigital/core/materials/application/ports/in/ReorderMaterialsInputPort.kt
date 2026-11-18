package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.ReorderMaterialsCommand
import br.com.ajadigital.core.shared.application.contracts.Page

fun interface ReorderMaterialsInputPort {
    fun reorder(request: ReorderMaterialsCommand): Page<Material>
}
