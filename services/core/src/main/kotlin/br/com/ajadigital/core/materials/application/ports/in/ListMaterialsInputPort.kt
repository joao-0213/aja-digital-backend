package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.ListMaterialsQuery
import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.shared.application.contracts.Page

fun interface ListMaterialsInputPort {
    fun materials(request: ListMaterialsQuery): Page<Material>
}
