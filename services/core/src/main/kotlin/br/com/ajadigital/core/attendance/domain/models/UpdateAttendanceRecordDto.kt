package br.com.ajadigital.core.attendance.domain.models

data class UpdateAttendanceRecordDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val revision: Int, val records: Map<String, Boolean>)
