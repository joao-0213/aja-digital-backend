package br.com.ajadigital.core.attendance.domain.usecases

import br.com.ajadigital.core.attendance.domain.ports.`in`.GetAttendanceSessionUseCase
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class GetAttendanceSession(
    private val store: AttendanceStore,
) : GetAttendanceSessionUseCase {
    override fun attendance(actor: AcademicActor, id: String): Map<String, Any?> {
        AttendanceSupport.readable(actor)
        return store.attendance(actor.id, actor.role, actor.sessionTokenHash, AttendanceSupport.checkedId(id)) ?: throw AcademicNotFound()
    }
}
