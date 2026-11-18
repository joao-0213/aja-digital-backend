package br.com.ajadigital.core.attendance.domain.models

data class CreateAttendanceRecordDto(val actorId: String, val role: String, val sessionHash: String, val draft: AttendanceDraft)
