package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ReorderMaterialsDto(val actor: AuthenticatedActor, val moduleId: String, val revision: Int, val items: List<Pair<String, Int>>, val page: Int, val size: Int)
