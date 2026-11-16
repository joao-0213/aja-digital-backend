package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.ports.`in`.ReorderMaterialsUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class ReorderMaterials(private val store: ContentStore) : ReorderMaterialsUseCase {
    override fun reorder(actor: AcademicActor, moduleId: String, revision: Int, items: List<Pair<String, Int>>, page: Int, size: Int): Map<String, Any?> {
        MaterialsSupport.writer(actor)
        require(MaterialsSupport.validId(moduleId) && revision > 0 && items.isNotEmpty() && items.map { it.first }.distinct().size == items.size && items.all { MaterialsSupport.validId(it.first) && it.second > 0 } && page > 0 && size in 1..100)
        return store.reorderMaterials(actor.id, actor.role, actor.sessionTokenHash, moduleId, revision, items, page, size)?.let(MaterialsSupport::page) ?: throw AcademicConflict()
    }
}
