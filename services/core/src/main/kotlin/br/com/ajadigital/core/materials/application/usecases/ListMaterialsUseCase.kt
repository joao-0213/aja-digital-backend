package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.domain.models.ContentStoreMaterialsDto

import br.com.ajadigital.core.materials.application.models.ListMaterialsDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage
import br.com.ajadigital.core.materials.application.ports.`in`.ListMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class ListMaterialsUseCase(private val store: ContentStoreOutputPort) : ListMaterialsInputPort {
    override fun materials(request: ListMaterialsDto): AcademicPage {
                val (actor, page, size, sort, moduleId, subjectId) = request
        MaterialsPolicy.reader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("order", "-order"))
        require(moduleId == null || MaterialsPolicy.validId(moduleId))
        require(subjectId == null || MaterialsPolicy.validId(subjectId))
        return store.materials(ContentStoreMaterialsDto(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, moduleId, subjectId))
    }
}
