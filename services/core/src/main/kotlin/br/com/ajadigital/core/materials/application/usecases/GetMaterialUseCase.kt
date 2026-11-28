package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.GetMaterialQuery
import br.com.ajadigital.core.materials.application.models.MaterialDetail
import br.com.ajadigital.core.materials.application.ports.`in`.GetMaterialInputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialReadOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.materials.domain.policies.MaterialRules

class GetMaterialUseCase(
    private val store: MaterialReadOutputPort,
) : GetMaterialInputPort {
    override fun material(request: GetMaterialQuery): MaterialDetail {
        MaterialRules.id(request.id)
        request.actor.requireSessionRole()
        return store.material(request) ?: throw MaterialsNotFound()
    }
}
