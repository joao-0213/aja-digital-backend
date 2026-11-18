package br.com.ajadigital.core.attendance.domain.models

data class GetStoredAttendanceDto(val actorId: String, val role: String, val sessionHash: String, val id: String)
