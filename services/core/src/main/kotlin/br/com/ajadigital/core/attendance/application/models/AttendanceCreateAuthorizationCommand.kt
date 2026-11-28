package br.com.ajadigital.core.attendance.application.models

data class AttendanceCreateAuthorizationCommand(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val subjectId: String,
)
