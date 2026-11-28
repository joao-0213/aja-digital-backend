package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class TransitionEnrollmentDto(
    val actor: AuthenticatedActor,
    val id: String,
    val revision: Int,
    val command: String,
    val key: String,
)
