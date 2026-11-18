package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.domain.models.ContentStoreDiscardDto

import br.com.ajadigital.core.materials.application.models.DiscardFileDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.ports.`in`.DiscardFileInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class DiscardFileUseCase(private val store: ContentStoreOutputPort) : DiscardFileInputPort {
    override fun discard(request: DiscardFileDto) = run { val (actor, id) = request; store.discard(ContentStoreDiscardDto(MaterialsPolicy.id(id), actor.id, actor.role, actor.sessionValidationReference)) }
}
