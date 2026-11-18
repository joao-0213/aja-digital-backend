package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ContentStoreMaterialDto

import br.com.ajadigital.core.materials.application.models.GetMaterialDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.ports.`in`.GetMaterialInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class GetMaterialUseCase(private val store: ContentStoreOutputPort) : GetMaterialInputPort {
    override fun material(request: GetMaterialDto): Map<String, Any?> = run { val (actor, id) = request; store.material(ContentStoreMaterialDto(MaterialsPolicy.id(id), actor.id, actor.role, actor.sessionValidationReference)) ?: throw AcademicNotFound() }
}
