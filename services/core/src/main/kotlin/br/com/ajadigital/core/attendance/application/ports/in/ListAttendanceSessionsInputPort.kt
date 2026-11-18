package br.com.ajadigital.core.attendance.application.ports.`in`
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.application.models.ListAttendanceSessionsDto
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

interface ListAttendanceSessionsInputPort {
    fun list(request: ListAttendanceSessionsDto): AcademicPage
}
