package br.com.ajadigital.core.attendance.application.models

import java.util.Collections

class AttendanceStaff(
    override val id: String, override val revision: Int, override val createdAt: String, override val updatedAt: String,
    override val updatedBy: String, override val subjectId: String, override val date: String, override val time: String, records: Map<String, Boolean>,
) : AttendanceRead {
    val records: Map<String, Boolean> = Collections.unmodifiableMap(LinkedHashMap(records))
}
