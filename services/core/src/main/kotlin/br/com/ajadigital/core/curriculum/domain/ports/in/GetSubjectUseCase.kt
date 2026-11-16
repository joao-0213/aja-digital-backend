package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetSubjectUseCase {
    fun subject(actor: AcademicActor, id: String): Map<String, Any?>
}
