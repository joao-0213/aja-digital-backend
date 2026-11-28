package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class SubmissionsListQuery(
    val actor: AuthenticatedActor,
    val page: Int,
    val pageSize: Int,
    val sort: String,
    val assessmentId: String?,
    val studentId: String?,
)
