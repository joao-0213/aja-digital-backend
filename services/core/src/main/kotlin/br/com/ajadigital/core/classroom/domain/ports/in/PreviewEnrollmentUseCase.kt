package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface PreviewEnrollmentUseCase {
    fun preview(actor: AcademicActor, classId: String, email: String): Map<String, Any?>
}
