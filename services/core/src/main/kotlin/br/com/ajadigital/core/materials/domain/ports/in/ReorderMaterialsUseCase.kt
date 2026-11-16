package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ReorderMaterialsUseCase {
    fun reorder(actor: AcademicActor, moduleId: String, revision: Int, items: List<Pair<String, Int>>, page: Int, size: Int): Map<String, Any?>
}
