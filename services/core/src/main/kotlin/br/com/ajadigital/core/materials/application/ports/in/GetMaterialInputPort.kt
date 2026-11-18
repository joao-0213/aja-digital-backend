package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.GetMaterialQuery
import br.com.ajadigital.core.materials.application.models.MaterialDetail

fun interface GetMaterialInputPort {
    fun material(request: GetMaterialQuery): MaterialDetail
}
