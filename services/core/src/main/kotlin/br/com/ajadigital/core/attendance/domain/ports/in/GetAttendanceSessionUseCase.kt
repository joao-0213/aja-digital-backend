package br.com.ajadigital.core.attendance.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetAttendanceSessionUseCase {
    fun attendance(actor: AcademicActor, id: String): Map<String, Any?>
}
