package br.com.ajadigital.core.attendance.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListEligibleAttendanceStudentsUseCase {
    fun eligible(actor: AcademicActor, subjectId: String, date: String, time: String, page: Int, size: Int): AcademicPage
}
