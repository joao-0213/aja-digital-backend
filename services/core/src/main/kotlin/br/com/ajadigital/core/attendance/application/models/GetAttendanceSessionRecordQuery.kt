package br.com.ajadigital.core.attendance.application.models

data class GetAttendanceSessionRecordQuery(val actorId: String, val role: String, val sessionHash: String, val id: String)
