package br.com.ajadigital.core.attendance.application.ports.`in`

import br.com.ajadigital.core.attendance.application.models.AttendanceEligibleStudentPage
import br.com.ajadigital.core.attendance.application.models.ListEligibleAttendanceStudentsQuery

interface ListEligibleAttendanceStudentsInputPort { fun eligible(query: ListEligibleAttendanceStudentsQuery): AttendanceEligibleStudentPage }
