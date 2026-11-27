package br.com.ajadigital.core.attendance.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListEligibleAttendanceStudentsQuery(val actor: AuthenticatedActor, val subjectId: String, val date: String, val time: String, val page: Int, val size: Int)
