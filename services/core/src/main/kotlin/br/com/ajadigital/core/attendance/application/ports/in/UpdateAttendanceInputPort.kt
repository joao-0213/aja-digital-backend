package br.com.ajadigital.core.attendance.application.ports.`in`

import br.com.ajadigital.core.attendance.application.models.AttendanceRead
import br.com.ajadigital.core.attendance.application.models.UpdateAttendanceCommand

interface UpdateAttendanceInputPort {
    fun update(command: UpdateAttendanceCommand): AttendanceRead
}
