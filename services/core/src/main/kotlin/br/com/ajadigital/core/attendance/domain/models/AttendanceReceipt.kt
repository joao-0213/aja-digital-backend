package br.com.ajadigital.core.attendance.domain.models

import java.util.Collections

class AttendanceReceipt(
    val id: String, val revision: Int, val createdAt: String, val updatedAt: String, val updatedBy: String,
    val subjectId: String, val date: String, val time: String, records: Map<String, Boolean>,
) {
    val records: Map<String, Boolean> = Collections.unmodifiableMap(LinkedHashMap(records))
}
