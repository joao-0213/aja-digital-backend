package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.ports.`in`.GetLearningProgressUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class GetLearningProgress(private val store: ContentStore) : GetLearningProgressUseCase {
    override fun progress(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?, materialId: String?): Map<String, Any?> {
        MaterialsSupport.reader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("id", "-id"))
        require(subjectId == null || MaterialsSupport.validId(subjectId))
        require(materialId == null || MaterialsSupport.validId(materialId))
        val result = store.progress(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, subjectId, materialId)
        return mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }
}
