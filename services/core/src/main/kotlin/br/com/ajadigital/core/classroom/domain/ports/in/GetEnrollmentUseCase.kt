package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetEnrollmentUseCase {
    fun enrollment(actor: AcademicActor, id: String): Map<String, Any?>
}
