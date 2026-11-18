package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetClassDto(val actor: AuthenticatedActor, val id: String)
