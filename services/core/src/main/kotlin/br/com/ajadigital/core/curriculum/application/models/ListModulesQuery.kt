package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListModulesQuery(
    val actor: AuthenticatedActor,
    val page: Int,
    val size: Int,
    val sort: String,
    val subjectId: String?,
)
