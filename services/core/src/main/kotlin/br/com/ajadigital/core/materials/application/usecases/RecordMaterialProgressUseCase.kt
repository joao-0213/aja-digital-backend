package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.contracts.LearningProgress
import br.com.ajadigital.core.materials.application.models.RecordMaterialProgressCommand
import br.com.ajadigital.core.materials.application.models.StoreRecordProgressCommand
import br.com.ajadigital.core.materials.application.ports.`in`.RecordMaterialProgressInputPort
import br.com.ajadigital.core.materials.application.ports.out.LearningProgressOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.materials.domain.policies.MaterialRules

class RecordMaterialProgressUseCase(
    private val store: LearningProgressOutputPort,
) : RecordMaterialProgressInputPort {
    override fun recordProgress(request: RecordMaterialProgressCommand): LearningProgress {
        val (actor, materialId, action) = request
        actor.requireReader()
        require(MaterialRules.validId(materialId) && action != null)
        return store.recordProgress(StoreRecordProgressCommand(actor, materialId, action)) ?: throw MaterialsNotFound()
    }
}
