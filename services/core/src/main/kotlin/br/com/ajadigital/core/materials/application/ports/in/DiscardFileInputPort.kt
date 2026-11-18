package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.DiscardFileDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface DiscardFileInputPort {
    fun discard(request: DiscardFileDto)
}
