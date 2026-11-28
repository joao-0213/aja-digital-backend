package br.com.ajadigital.core.attendance.application.usecases

import br.com.ajadigital.core.attendance.application.models.AttendanceSessionPage
import br.com.ajadigital.core.attendance.application.models.ListAttendanceSessionsQuery
import br.com.ajadigital.core.attendance.application.models.ListAttendanceSessionsRecordQuery
import br.com.ajadigital.core.attendance.application.ports.`in`.ListAttendanceSessionsInputPort
import br.com.ajadigital.core.attendance.application.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy

class ListAttendanceSessionsUseCase(
    private val store: AttendanceStoreOutputPort,
) : ListAttendanceSessionsInputPort {
    override fun list(query: ListAttendanceSessionsQuery): AttendanceSessionPage {
        AttendancePolicy.readable(query.actor.role, query.actor.active, query.actor.emailConfirmedAt != null)
        require(query.page > 0 && query.size in 1..MAX_PAGE_SIZE && query.sort in setOf("date", "-date"))
        require(query.subjectId == null || AttendancePolicy.validId(query.subjectId))
        require(query.studentId == null || AttendancePolicy.validId(query.studentId))
        val start = query.from?.let(AttendancePolicy::date)
        val end = query.to?.let(AttendancePolicy::date)
        require(start == null || end == null || !start.isAfter(end))
        val page =
            store.list(
                ListAttendanceSessionsRecordQuery(
                    query.actor.id,
                    query.actor.role,
                    query.actor.sessionValidationReference,
                    query.page,
                    query.size,
                    query.sort,
                    query.subjectId,
                    query.studentId,
                    start,
                    end,
                ),
            )
        return AttendanceSessionPage(page.items.map { it.toAttendanceRead() }, page.page, page.pageSize, page.total)
    }
}

private const val MAX_PAGE_SIZE = 100
