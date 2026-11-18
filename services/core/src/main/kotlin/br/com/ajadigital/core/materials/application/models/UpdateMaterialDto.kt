package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class UpdateMaterialDto(val actor: AuthenticatedActor, val id: String, val revision: Int, val title: String?, val duration: Double?, val content: String?, val videoUrl: String?)
