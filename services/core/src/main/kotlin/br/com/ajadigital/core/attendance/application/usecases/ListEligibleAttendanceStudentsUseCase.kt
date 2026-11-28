package br.com.ajadigital.core.attendance.application.usecases

import br.com.ajadigital.core.attendance.application.models.AttendanceEligibleStudent
import br.com.ajadigital.core.attendance.application.models.AttendanceEligibleStudentPage
import br.com.ajadigital.core.attendance.application.models.ListEligibleAttendanceStudentsQuery
import br.com.ajadigital.core.attendance.application.models.ListEligibleAttendanceStudentsRecordQuery
import br.com.ajadigital.core.attendance.application.ports.`in`.ListEligibleAttendanceStudentsInputPort
import br.com.ajadigital.core.attendance.application.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy
import java.time.LocalTime

class ListEligibleAttendanceStudentsUseCase(
    private val store: AttendanceStoreOutputPort,
) : ListEligibleAttendanceStudentsInputPort {
    override fun eligible(query: ListEligibleAttendanceStudentsQuery): AttendanceEligibleStudentPage {
        AttendancePolicy.writable(query.actor.role, query.actor.active, query.actor.emailConfirmedAt != null)
        require(
            AttendancePolicy.validId(query.subjectId) && query.page > 0 && query.size in 1..MAX_PAGE_SIZE &&
                query.time.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")),
        )
        val page =
            store.eligible(
                ListEligibleAttendanceStudentsRecordQuery(
                    query.actor.id,
                    query.actor.role,
                    query.actor.sessionValidationReference,
                    query.subjectId,
                    AttendancePolicy.date(query.date),
                    LocalTime.parse(query.time),
                    query.page,
                    query.size,
                ),
            )
        return AttendanceEligibleStudentPage(
            page.items.map {
                AttendanceEligibleStudent(it.id, it.name, it.eligible, it.reason)
            },
            page.page,
            page.pageSize,
            page.total,
        )
    }
}

private const val MAX_PAGE_SIZE = 100
