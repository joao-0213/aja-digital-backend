package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.domain.models.ContentStoreFileDto

import br.com.ajadigital.core.materials.application.models.GetFileMetadataDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.ports.`in`.GetFileMetadataInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class GetFileMetadataUseCase(private val store: ContentStoreOutputPort) : GetFileMetadataInputPort {
    override fun file(request: GetFileMetadataDto): Map<String, Any?> = run { val (actor, id) = request; store.file(ContentStoreFileDto(MaterialsPolicy.id(id), actor.id, actor.role, actor.sessionValidationReference)) ?: throw AcademicNotFound() }
}
