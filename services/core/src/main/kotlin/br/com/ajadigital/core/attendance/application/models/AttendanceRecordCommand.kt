package br.com.ajadigital.core.attendance.application.models

import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft

data class AttendanceRecordCommand(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val draft: AttendanceDraft,
)
