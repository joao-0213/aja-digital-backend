package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetMaterialQuery(
    val actor: AuthenticatedActor,
    val id: String,
)
