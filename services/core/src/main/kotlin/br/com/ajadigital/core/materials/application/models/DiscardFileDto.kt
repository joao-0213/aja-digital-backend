package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class DiscardFileDto(val actor: AuthenticatedActor, val id: String)
