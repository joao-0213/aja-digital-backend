package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.curriculum.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class UpdateModuleDto(val actor: AuthenticatedActor, val id: String, val revision: Int, val title: String?, val description: String?)
