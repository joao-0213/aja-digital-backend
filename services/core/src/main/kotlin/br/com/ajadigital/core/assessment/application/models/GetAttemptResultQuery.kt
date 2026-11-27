package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetAttemptResultQuery(val actor: AuthenticatedActor, val id: String)
