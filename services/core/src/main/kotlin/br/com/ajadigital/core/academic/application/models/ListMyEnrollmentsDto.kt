package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListMyEnrollmentsDto(
    val actor: AuthenticatedActor,
    val page: Int,
    val size: Int,
    val sort: String,
)
