package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetAttemptResultUseCase {
    fun result(actor: AcademicActor, id: String): Map<String, Any?>
}
