package br.com.ajadigital.core.materials.application.ports.out

import br.com.ajadigital.core.materials.application.models.GetMaterialQuery
import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.MaterialDetail
import br.com.ajadigital.core.materials.application.models.StoreMaterialsQuery
import br.com.ajadigital.core.shared.application.contracts.Page

interface MaterialReadOutputPort {
    fun materials(request: StoreMaterialsQuery): Page<Material>
    fun material(request: GetMaterialQuery): MaterialDetail?
}
