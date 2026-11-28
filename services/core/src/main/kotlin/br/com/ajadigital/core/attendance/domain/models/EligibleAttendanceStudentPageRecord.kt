package br.com.ajadigital.core.attendance.domain.models

data class EligibleAttendanceStudentPageRecord(
    val items: List<EligibleAttendanceStudentRecord>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)
