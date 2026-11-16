package br.com.ajadigital.core.attendance.domain.usecases

import br.com.ajadigital.core.attendance.domain.ports.`in`.ListAttendanceSessionsUseCase
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

class ListAttendanceSessions(
    private val store: AttendanceStore,
) : ListAttendanceSessionsUseCase {
    override fun list(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?, studentId: String?, from: String?, to: String?): AcademicPage {
        AttendanceSupport.readable(actor)
        require(page > 0 && size in 1..100 && sort in setOf("date", "-date"))
        require(subjectId == null || AttendanceSupport.validId(subjectId))
        require(studentId == null || AttendanceSupport.validId(studentId))
        val start = from?.let(AttendanceSupport::date)
        val end = to?.let(AttendanceSupport::date)
        require(start == null || end == null || !start.isAfter(end))
        return store.list(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, subjectId, studentId, start, end)
    }
}
