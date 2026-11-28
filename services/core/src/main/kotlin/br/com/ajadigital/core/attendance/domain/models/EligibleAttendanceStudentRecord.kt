package br.com.ajadigital.core.attendance.domain.models

data class EligibleAttendanceStudentRecord(
    val id: String,
    val name: String,
    val eligible: Boolean,
    val reason: String?,
)
