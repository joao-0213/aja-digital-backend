package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetSubjectQuery(val actor: AuthenticatedActor, val id: String)
