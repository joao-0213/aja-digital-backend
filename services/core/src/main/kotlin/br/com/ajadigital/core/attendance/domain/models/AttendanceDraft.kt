package br.com.ajadigital.core.attendance.domain.models

import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections

class AttendanceDraft(
    val subjectId: String,
    val date: LocalDate,
    val time: LocalTime,
    records: Map<String, Boolean>,
) {
    val records: Map<String, Boolean> = Collections.unmodifiableMap(LinkedHashMap(records))

    fun withRecords(records: Map<String, Boolean>) = AttendanceDraft(subjectId, date, time, records)
}
