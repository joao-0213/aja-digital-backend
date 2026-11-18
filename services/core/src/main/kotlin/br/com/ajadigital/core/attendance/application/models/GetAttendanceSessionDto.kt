package br.com.ajadigital.core.attendance.application.models

import br.com.ajadigital.core.attendance.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetAttendanceSessionDto(val actor: AuthenticatedActor, val id: String)
