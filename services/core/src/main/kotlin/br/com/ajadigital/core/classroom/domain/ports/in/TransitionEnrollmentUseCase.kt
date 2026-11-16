package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface TransitionEnrollmentUseCase {
    fun transitionEnrollment(actor: AcademicActor, id: String, revision: Int, command: String, key: String): Map<String, Any?>
}
