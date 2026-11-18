package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetLearningProgressDto(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: String, val subjectId: String?, val materialId: String?)
