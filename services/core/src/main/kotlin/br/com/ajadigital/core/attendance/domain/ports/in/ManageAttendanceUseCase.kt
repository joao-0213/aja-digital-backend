package br.com.ajadigital.core.attendance.domain.ports.`in`

import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ManageAttendanceUseCase {
    fun list(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?, studentId: String?, from: String?, to: String?): AcademicPage
    fun eligible(actor: AcademicActor, subjectId: String, date: String, time: String, page: Int, size: Int): AcademicPage
    fun attendance(actor: AcademicActor, id: String): Map<String, Any?>
    fun create(actor: AcademicActor, draft: AttendanceDraft, key: String): Map<String, Any?>
    fun update(actor: AcademicActor, id: String, revision: Int, records: Map<String, Boolean>): Map<String, Any?>
}
