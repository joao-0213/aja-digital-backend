package br.com.ajadigital.core.attendance.application.usecases

import br.com.ajadigital.core.attendance.application.models.AttendanceRead
import br.com.ajadigital.core.attendance.application.models.AttendanceStaff
import br.com.ajadigital.core.attendance.application.models.AttendanceStudent
import br.com.ajadigital.core.attendance.domain.models.AttendanceSessionRecord

internal fun AttendanceSessionRecord.toAttendanceRead(): AttendanceRead = if (studentView) {
    AttendanceStudent(
        id,
        revision,
        createdAt,
        updatedAt,
        updatedBy,
        subjectId,
        date.toString(),
        time.toString().substring(0, MINUTE_TIME_LENGTH),
        records.values.firstOrNull(),
    )
} else {
    AttendanceStaff(id, revision, createdAt, updatedAt, updatedBy, subjectId, date.toString(), time.toString().substring(0, MINUTE_TIME_LENGTH), records)
}

private const val MINUTE_TIME_LENGTH = 5
