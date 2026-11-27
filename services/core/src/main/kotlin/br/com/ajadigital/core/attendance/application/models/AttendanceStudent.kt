package br.com.ajadigital.core.attendance.application.models

data class AttendanceStudent(
    override val id: String, override val revision: Int, override val createdAt: String, override val updatedAt: String,
    override val updatedBy: String, override val subjectId: String, override val date: String, override val time: String,
    val present: Boolean?,
) : AttendanceRead
