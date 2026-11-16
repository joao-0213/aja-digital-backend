package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetSubmissionUseCase {
    fun submission(actor: AcademicActor, id: String): Map<String, Any?>
}
