package br.com.ajadigital.core.attendance.application.ports.`in`

import br.com.ajadigital.core.attendance.application.models.AttendanceSessionPage
import br.com.ajadigital.core.attendance.application.models.ListAttendanceSessionsQuery

interface ListAttendanceSessionsInputPort { fun list(query: ListAttendanceSessionsQuery): AttendanceSessionPage }
