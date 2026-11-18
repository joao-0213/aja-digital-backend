package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetFileMetadataQuery(val actor: AuthenticatedActor, val id: String)
