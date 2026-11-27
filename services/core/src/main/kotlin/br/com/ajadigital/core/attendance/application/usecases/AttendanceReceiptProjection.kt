package br.com.ajadigital.core.attendance.application.usecases

import br.com.ajadigital.core.attendance.application.models.AttendanceStaff
import br.com.ajadigital.core.attendance.domain.models.AttendanceReceipt

internal fun AttendanceReceipt.toStaffView() = AttendanceStaff(id, revision, createdAt, updatedAt, updatedBy, subjectId, date, time, records)
