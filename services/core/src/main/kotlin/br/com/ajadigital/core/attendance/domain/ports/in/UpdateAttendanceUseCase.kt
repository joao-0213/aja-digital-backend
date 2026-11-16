package br.com.ajadigital.core.attendance.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface UpdateAttendanceUseCase {
    fun update(actor: AcademicActor, id: String, revision: Int, records: Map<String, Boolean>): Map<String, Any?>
}
