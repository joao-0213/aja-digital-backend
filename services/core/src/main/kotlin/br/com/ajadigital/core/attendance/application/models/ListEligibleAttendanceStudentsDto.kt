package br.com.ajadigital.core.attendance.application.models

import br.com.ajadigital.core.attendance.domain.models.*
import br.com.ajadigital.core.academic.application.models.AcademicPage

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListEligibleAttendanceStudentsDto(val actor: AuthenticatedActor, val subjectId: String, val date: String, val time: String, val page: Int, val size: Int)
