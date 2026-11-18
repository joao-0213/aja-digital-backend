package br.com.ajadigital.core.attendance.application.usecases
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy
import br.com.ajadigital.core.attendance.application.ports.`in`.ListAttendanceSessionsInputPort
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.attendance.application.models.ListAttendanceSessionsDto
import br.com.ajadigital.core.attendance.domain.models.ListAttendanceDto
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

class ListAttendanceSessionsUseCase(
    private val store: AttendanceStoreOutputPort,
) : ListAttendanceSessionsInputPort {
    override fun list(request: ListAttendanceSessionsDto): AcademicPage {
        val (actor, page, size, sort, subjectId, studentId, from, to) = request
                AttendancePolicy.readable(actor)
        require(page > 0 && size in 1..100 && sort in setOf("date", "-date"))
        require(subjectId == null || AttendancePolicy.validId(subjectId))
        require(studentId == null || AttendancePolicy.validId(studentId))
        val start = from?.let(AttendancePolicy::date)
        val end = to?.let(AttendancePolicy::date)
        require(start == null || end == null || !start.isAfter(end))
        return store.list(ListAttendanceDto(actor.id, actor.role, actor.sessionValidationReference, page, size, sort, subjectId, studentId, start, end))
    }
}
