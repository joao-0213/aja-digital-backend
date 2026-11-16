package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.materials.domain.ports.`in`.ListMaterialsUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class ListMaterials(private val store: ContentStore) : ListMaterialsUseCase {
    override fun materials(actor: AcademicActor, page: Int, size: Int, sort: String, moduleId: String?, subjectId: String?): AcademicPage {
        MaterialsSupport.reader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("order", "-order"))
        require(moduleId == null || MaterialsSupport.validId(moduleId))
        require(subjectId == null || MaterialsSupport.validId(subjectId))
        return store.materials(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, moduleId, subjectId)
    }
}
