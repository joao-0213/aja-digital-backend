package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ContentStoreRecordProgressDto

import br.com.ajadigital.core.materials.application.models.RecordMaterialProgressDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.ports.`in`.RecordMaterialProgressInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class RecordMaterialProgressUseCase(private val store: ContentStoreOutputPort) : RecordMaterialProgressInputPort {
    override fun recordProgress(request: RecordMaterialProgressDto): Map<String, Any?> {
                val (actor, materialId, completed) = request
        MaterialsPolicy.reader(actor)
        require(MaterialsPolicy.validId(materialId) && completed != false)
        return store.recordProgress(ContentStoreRecordProgressDto(actor.id, actor.role, actor.sessionValidationReference, materialId, completed == true)) ?: throw AcademicNotFound()
    }
}
