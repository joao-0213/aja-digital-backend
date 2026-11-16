package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.ports.`in`.RecordMaterialProgressUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class RecordMaterialProgress(private val store: ContentStore) : RecordMaterialProgressUseCase {
    override fun recordProgress(actor: AcademicActor, materialId: String, completed: Boolean?): Map<String, Any?> {
        MaterialsSupport.reader(actor)
        require(MaterialsSupport.validId(materialId) && completed != false)
        return store.recordProgress(actor.id, actor.role, actor.sessionTokenHash, materialId, completed == true) ?: throw AcademicNotFound()
    }
}
