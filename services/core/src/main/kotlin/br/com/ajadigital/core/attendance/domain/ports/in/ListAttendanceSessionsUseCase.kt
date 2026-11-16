package br.com.ajadigital.core.attendance.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListAttendanceSessionsUseCase {
    fun list(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?, studentId: String?, from: String?, to: String?): AcademicPage
}
