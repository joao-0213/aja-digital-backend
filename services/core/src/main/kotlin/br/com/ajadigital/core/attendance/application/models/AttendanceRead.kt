package br.com.ajadigital.core.attendance.application.models

sealed interface AttendanceRead {
    val id: String
    val revision: Int
    val createdAt: String
    val updatedAt: String
    val updatedBy: String
    val subjectId: String
    val date: String
    val time: String
}
