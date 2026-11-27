package br.com.ajadigital.core.attendance.application.models

import java.time.LocalDate
import java.time.LocalTime

data class ListEligibleAttendanceStudentsRecordQuery(val actorId: String, val role: String, val sessionHash: String, val subjectId: String, val date: LocalDate, val time: LocalTime, val page: Int, val size: Int)
