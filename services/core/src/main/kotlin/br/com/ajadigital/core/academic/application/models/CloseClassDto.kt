package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CloseClassDto(
    val actor: AuthenticatedActor,
    val id: String,
    val revision: Int,
    val key: String,
)
