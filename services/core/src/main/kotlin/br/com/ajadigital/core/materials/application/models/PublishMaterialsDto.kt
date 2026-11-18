package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class PublishMaterialsDto(val actor: AuthenticatedActor, val key: String, val moduleId: String, val revision: Int, val items: List<MaterialCreate>)
