package br.com.ajadigital.core.attendance.application.ports.`in`

import br.com.ajadigital.core.attendance.application.models.AttendanceRead
import br.com.ajadigital.core.attendance.application.models.GetAttendanceSessionQuery

interface GetAttendanceSessionInputPort {
    fun attendance(query: GetAttendanceSessionQuery): AttendanceRead
}
