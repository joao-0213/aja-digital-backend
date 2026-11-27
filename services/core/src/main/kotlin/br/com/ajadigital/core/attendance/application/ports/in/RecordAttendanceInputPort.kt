package br.com.ajadigital.core.attendance.application.ports.`in`

import br.com.ajadigital.core.attendance.application.models.AttendanceRead
import br.com.ajadigital.core.attendance.application.models.RecordAttendanceCommand

interface RecordAttendanceInputPort { fun create(command: RecordAttendanceCommand): AttendanceRead }
