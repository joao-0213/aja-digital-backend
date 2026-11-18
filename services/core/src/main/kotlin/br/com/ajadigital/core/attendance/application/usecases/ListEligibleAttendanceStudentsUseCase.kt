package br.com.ajadigital.core.attendance.application.usecases
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy
import br.com.ajadigital.core.attendance.application.ports.`in`.ListEligibleAttendanceStudentsInputPort
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.attendance.application.models.ListEligibleAttendanceStudentsDto
import br.com.ajadigital.core.attendance.domain.models.ListEligibleAttendanceDto
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage
import java.time.LocalTime

class ListEligibleAttendanceStudentsUseCase(
    private val store: AttendanceStoreOutputPort,
) : ListEligibleAttendanceStudentsInputPort {
    override fun eligible(request: ListEligibleAttendanceStudentsDto): AcademicPage {
        val (actor, subjectId, date, time, page, size) = request
                AttendancePolicy.writable(actor)
        require(AttendancePolicy.validId(subjectId) && page > 0 && size in 1..100 && time.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")))
        return store.eligible(ListEligibleAttendanceDto(actor.id, actor.role, actor.sessionValidationReference, subjectId, AttendancePolicy.date(date), LocalTime.parse(time), page, size))
    }
}
