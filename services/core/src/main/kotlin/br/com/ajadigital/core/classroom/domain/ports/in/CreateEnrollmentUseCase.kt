package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CreateEnrollmentUseCase {
    fun createEnrollment(actor: AcademicActor, classId: String, emailInput: String, key: String): Map<String, Any?>
}
