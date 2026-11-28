package br.com.ajadigital.core.attendance.application.usecases

import br.com.ajadigital.core.attendance.domain.exceptions.AttendanceNotFound
import br.com.ajadigital.core.attendance.application.models.GetAttendanceSessionQuery
import br.com.ajadigital.core.attendance.application.ports.`in`.GetAttendanceSessionInputPort
import br.com.ajadigital.core.attendance.application.models.GetAttendanceSessionRecordQuery
import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy
import br.com.ajadigital.core.attendance.application.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.attendance.application.models.AttendanceRead

class GetAttendanceSessionUseCase(private val store: AttendanceStoreOutputPort) : GetAttendanceSessionInputPort {
    override fun attendance(query: GetAttendanceSessionQuery): AttendanceRead {
        AttendancePolicy.readable(query.actor.role, query.actor.active, query.actor.emailConfirmedAt != null)
        return store.attendance(GetAttendanceSessionRecordQuery(query.actor.id, query.actor.role, query.actor.sessionValidationReference, AttendancePolicy.checkedId(query.id)))
            ?.toAttendanceRead() ?: throw AttendanceNotFound()
    }
}
