package br.com.ajadigital.core.attendance.application.models

import br.com.ajadigital.core.attendance.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class RecordAttendanceDto(val actor: AuthenticatedActor, val draft: AttendanceDraft, val key: String)
