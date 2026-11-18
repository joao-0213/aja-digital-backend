package br.com.ajadigital.core.attendance.application.models

import br.com.ajadigital.core.attendance.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class UpdateAttendanceDto(val actor: AuthenticatedActor, val id: String, val revision: Int, val records: Map<String, Boolean>)
