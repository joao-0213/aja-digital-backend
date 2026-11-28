package br.com.ajadigital.core.forum.application.contracts

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListDashboardTopicsQuery(
    val actor: AuthenticatedActor,
)
