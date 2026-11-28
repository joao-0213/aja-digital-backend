package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateClassDto(
    val actor: AuthenticatedActor,
    val name: String,
    val code: String,
    val year: Int,
    val shift: String,
    val key: String,
)
