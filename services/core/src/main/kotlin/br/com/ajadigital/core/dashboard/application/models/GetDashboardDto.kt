package br.com.ajadigital.core.dashboard.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetDashboardDto(val actor: AuthenticatedActor)
