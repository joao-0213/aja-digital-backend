package br.com.ajadigital.core.assessment.application.contracts

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListDashboardAssessmentsQuery(
    val actor: AuthenticatedActor,
    val pageSize: Int = 100,
)
