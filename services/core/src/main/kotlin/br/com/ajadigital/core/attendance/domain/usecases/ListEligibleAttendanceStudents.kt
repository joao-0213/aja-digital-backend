package br.com.ajadigital.core.attendance.domain.usecases

import br.com.ajadigital.core.attendance.domain.ports.`in`.ListEligibleAttendanceStudentsUseCase
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import java.time.LocalTime

class ListEligibleAttendanceStudents(
    private val store: AttendanceStore,
) : ListEligibleAttendanceStudentsUseCase {
    override fun eligible(actor: AcademicActor, subjectId: String, date: String, time: String, page: Int, size: Int): AcademicPage {
        AttendanceSupport.writable(actor)
        require(AttendanceSupport.validId(subjectId) && page > 0 && size in 1..100 && time.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")))
        return store.eligible(actor.id, actor.role, actor.sessionTokenHash, subjectId, AttendanceSupport.date(date), LocalTime.parse(time), page, size)
    }
}
