package br.com.ajadigital.core.attendance.domain.exceptions

class AttendanceConflict(
    val currentRevision: Int? = null,
) : RuntimeException()
