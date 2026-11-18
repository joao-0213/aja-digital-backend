package br.com.ajadigital.core.attendance.application.ports.`in`
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.application.models.UpdateAttendanceDto

interface UpdateAttendanceInputPort {
    fun update(request: UpdateAttendanceDto): Map<String, Any?>
}
