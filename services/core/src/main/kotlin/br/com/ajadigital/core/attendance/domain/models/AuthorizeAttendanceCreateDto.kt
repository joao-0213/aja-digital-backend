package br.com.ajadigital.core.attendance.domain.models

data class AuthorizeAttendanceCreateDto(val actorId: String, val role: String, val sessionHash: String, val subjectId: String)
