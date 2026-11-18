package br.com.ajadigital.core.attendance.application.ports.`in`
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.application.models.RecordAttendanceDto

interface RecordAttendanceInputPort {
    fun create(request: RecordAttendanceDto): Map<String, Any?>
}
