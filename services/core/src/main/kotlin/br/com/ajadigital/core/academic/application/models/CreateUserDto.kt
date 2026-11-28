package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateUserDto(
    val actor: AuthenticatedActor,
    val name: String,
    val emailInput: String,
    val role: String,
    val key: String,
)
