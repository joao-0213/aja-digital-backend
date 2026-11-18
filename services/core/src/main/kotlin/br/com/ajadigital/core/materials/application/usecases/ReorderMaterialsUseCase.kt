package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ContentStoreReorderMaterialsDto

import br.com.ajadigital.core.materials.application.models.ReorderMaterialsDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.ports.`in`.ReorderMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class ReorderMaterialsUseCase(private val store: ContentStoreOutputPort) : ReorderMaterialsInputPort {
    override fun reorder(request: ReorderMaterialsDto): Map<String, Any?> {
        val (actor, moduleId, revision, items, page, size) = request
                MaterialsPolicy.writer(actor)
        require(MaterialsPolicy.validId(moduleId) && revision > 0 && items.isNotEmpty() && items.map { it.first }.distinct().size == items.size && items.all { MaterialsPolicy.validId(it.first) && it.second > 0 } && page > 0 && size in 1..100)
        return store.reorderMaterials(ContentStoreReorderMaterialsDto(actor.id, actor.role, actor.sessionValidationReference, moduleId, revision, items, page, size))?.let(MaterialsPolicy::page) ?: throw AcademicConflict()
    }
}
