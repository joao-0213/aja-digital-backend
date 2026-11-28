package br.com.ajadigital.core.attendance.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListAttendanceSessionsQuery(
    val actor: AuthenticatedActor,
    val page: Int,
    val size: Int,
    val sort: String,
    val subjectId: String?,
    val studentId: String?,
    val from: String?,
    val to: String?,
)
