package br.com.ajadigital.core.attendance.application.usecases
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy
import br.com.ajadigital.core.attendance.application.ports.`in`.GetAttendanceSessionInputPort
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.attendance.application.models.GetAttendanceSessionDto
import br.com.ajadigital.core.attendance.domain.models.GetStoredAttendanceDto
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class GetAttendanceSessionUseCase(
    private val store: AttendanceStoreOutputPort,
) : GetAttendanceSessionInputPort {
    override fun attendance(request: GetAttendanceSessionDto): Map<String, Any?> {
        val (actor, id) = request
                AttendancePolicy.readable(actor)
        return store.attendance(GetStoredAttendanceDto(actor.id, actor.role, actor.sessionValidationReference, AttendancePolicy.checkedId(id))) ?: throw AcademicNotFound()
    }
}
