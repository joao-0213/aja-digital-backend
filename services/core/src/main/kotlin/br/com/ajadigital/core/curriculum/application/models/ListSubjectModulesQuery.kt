package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListSubjectModulesQuery(
    val actor: AuthenticatedActor,
    val subjectId: String,
)
