package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.ports.`in`.GetMaterialUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class GetMaterial(private val store: ContentStore) : GetMaterialUseCase {
    override fun material(actor: AcademicActor, id: String): Map<String, Any?> =
        store.material(MaterialsSupport.id(id), actor.id, actor.role, actor.sessionTokenHash) ?: throw AcademicNotFound()
}
