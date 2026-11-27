package br.com.ajadigital.core.attendance.application.models

import java.time.LocalDate

data class ListAttendanceSessionsRecordQuery(val actorId: String, val role: String, val sessionHash: String, val page: Int, val size: Int, val sort: String, val subjectId: String?, val studentId: String?, val from: LocalDate?, val to: LocalDate?)
