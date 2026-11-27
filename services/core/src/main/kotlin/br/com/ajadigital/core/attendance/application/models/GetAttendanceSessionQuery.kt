package br.com.ajadigital.core.attendance.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetAttendanceSessionQuery(val actor: AuthenticatedActor, val id: String)
