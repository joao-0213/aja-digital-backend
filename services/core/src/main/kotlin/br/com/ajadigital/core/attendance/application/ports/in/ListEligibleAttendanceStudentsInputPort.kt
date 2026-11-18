package br.com.ajadigital.core.attendance.application.ports.`in`
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.application.models.ListEligibleAttendanceStudentsDto
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

interface ListEligibleAttendanceStudentsInputPort {
    fun eligible(request: ListEligibleAttendanceStudentsDto): AcademicPage
}
