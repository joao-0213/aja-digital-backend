package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.contracts.LearningProgress
import br.com.ajadigital.core.materials.application.models.GetLearningProgressQuery
import br.com.ajadigital.core.materials.application.models.StoreProgressQuery
import br.com.ajadigital.core.materials.application.ports.`in`.GetLearningProgressInputPort
import br.com.ajadigital.core.materials.application.ports.out.LearningProgressOutputPort
import br.com.ajadigital.core.materials.domain.policies.MaterialRules
import br.com.ajadigital.core.shared.application.contracts.Page

class GetLearningProgressUseCase(
    private val store: LearningProgressOutputPort,
) : GetLearningProgressInputPort {
    override fun progress(request: GetLearningProgressQuery): Page<LearningProgress> {
        val actor = request.actor
        val page = request.page
        val size = request.size
        val sort = request.sort
        val subjectId = request.subjectId
        val materialId = request.materialId
        actor.requireReader()
        require(page > 0 && size in 1..MAX_PAGE_SIZE && sort != null)
        require(subjectId == null || MaterialRules.validId(subjectId))
        require(materialId == null || MaterialRules.validId(materialId))
        return store.progress(StoreProgressQuery(actor, page, size, sort, subjectId, materialId))
    }
}

private const val MAX_PAGE_SIZE = 100
