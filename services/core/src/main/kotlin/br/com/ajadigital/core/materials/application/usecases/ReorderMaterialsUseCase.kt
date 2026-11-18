package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.ReorderMaterialsCommand
import br.com.ajadigital.core.materials.application.ports.`in`.ReorderMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialWriteOutputPort
import br.com.ajadigital.core.materials.domain.policies.MaterialRules
import br.com.ajadigital.core.shared.application.contracts.Page

class ReorderMaterialsUseCase(private val store: MaterialWriteOutputPort) : ReorderMaterialsInputPort {
    override fun reorder(request: ReorderMaterialsCommand): Page<Material> {
        val actor = request.actor
        val moduleId = request.moduleId
        val revision = request.expectedRevision
        val items = request.items
        val page = request.page
        val size = request.size
        actor.requireWriter()
        require(MaterialRules.validId(moduleId) && revision > 0 && items.isNotEmpty() &&
            items.map { it.id }.distinct().size == items.size && items.all { MaterialRules.validId(it.id) && it.revision > 0 } &&
            page > 0 && size in 1..100)
        return store.reorderMaterials(request)
    }
}
