package br.com.ajadigital.core.attendance.application.models

import java.util.Collections

class UpdateAttendanceRecordCommand(val actorId: String, val role: String, val sessionHash: String, val id: String, val revision: Int, records: Map<String, Boolean>) {
    val records: Map<String, Boolean> = Collections.unmodifiableMap(LinkedHashMap(records))
}
