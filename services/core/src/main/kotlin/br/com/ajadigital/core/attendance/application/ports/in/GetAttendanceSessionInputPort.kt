package br.com.ajadigital.core.attendance.application.ports.`in`
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.application.models.GetAttendanceSessionDto

interface GetAttendanceSessionInputPort {
    fun attendance(request: GetAttendanceSessionDto): Map<String, Any?>
}
