package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.ListMaterialsQuery
import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.StoreMaterialsQuery
import br.com.ajadigital.core.materials.application.ports.`in`.ListMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialReadOutputPort
import br.com.ajadigital.core.materials.domain.policies.MaterialRules
import br.com.ajadigital.core.shared.application.contracts.Page

class ListMaterialsUseCase(
    private val store: MaterialReadOutputPort,
) : ListMaterialsInputPort {
    override fun materials(request: ListMaterialsQuery): Page<Material> {
        val actor = request.actor
        val page = request.page
        val size = request.size
        val sort = request.sort
        val moduleId = request.moduleId
        val subjectId = request.subjectId
        actor.requireReader()
        require(page > 0 && size in 1..MAX_PAGE_SIZE && sort != null)
        require(moduleId == null || MaterialRules.validId(moduleId))
        require(subjectId == null || MaterialRules.validId(subjectId))
        return store.materials(StoreMaterialsQuery(actor, page, size, sort, moduleId, subjectId))
    }
}

private const val MAX_PAGE_SIZE = 100
