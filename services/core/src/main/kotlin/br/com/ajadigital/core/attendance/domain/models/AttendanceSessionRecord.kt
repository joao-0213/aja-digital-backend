package br.com.ajadigital.core.attendance.domain.models

import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections

class AttendanceSessionRecord(
    val id: String, val revision: Int, val createdAt: String, val updatedAt: String, val updatedBy: String,
    val subjectId: String, val classId: String, val date: LocalDate, val time: LocalTime,
    records: Map<String, Boolean>, val studentView: Boolean,
) {
    val records: Map<String, Boolean> = Collections.unmodifiableMap(LinkedHashMap(records))

    fun viewed(records: Map<String, Boolean>, studentView: Boolean) = AttendanceSessionRecord(
        id, revision, createdAt, updatedAt, updatedBy, subjectId, classId, date, time, records, studentView,
    )
}
