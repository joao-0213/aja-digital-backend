package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListDashboardClassesDto(
    val actor: AuthenticatedActor,
    val pageSize: Int = 100,
)
