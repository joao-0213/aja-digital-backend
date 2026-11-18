package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ContentStoreProgressDto

import br.com.ajadigital.core.materials.application.models.GetLearningProgressDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.ports.`in`.GetLearningProgressInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class GetLearningProgressUseCase(private val store: ContentStoreOutputPort) : GetLearningProgressInputPort {
    override fun progress(request: GetLearningProgressDto): Map<String, Any?> {
                val (actor, page, size, sort, subjectId, materialId) = request
        MaterialsPolicy.reader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("id", "-id"))
        require(subjectId == null || MaterialsPolicy.validId(subjectId))
        require(materialId == null || MaterialsPolicy.validId(materialId))
        val result = store.progress(ContentStoreProgressDto(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, subjectId, materialId))
        return mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }
}
