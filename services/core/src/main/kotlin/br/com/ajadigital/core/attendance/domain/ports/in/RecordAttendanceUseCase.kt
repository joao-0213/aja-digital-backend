package br.com.ajadigital.core.attendance.domain.ports.`in`

import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface RecordAttendanceUseCase {
    fun create(actor: AcademicActor, draft: AttendanceDraft, key: String): Map<String, Any?>
}
