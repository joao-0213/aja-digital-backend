package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class RecordMaterialProgressDto(val actor: AuthenticatedActor, val materialId: String, val completed: Boolean?)
