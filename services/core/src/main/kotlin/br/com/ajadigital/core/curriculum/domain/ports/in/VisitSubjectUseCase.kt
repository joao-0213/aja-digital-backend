package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface VisitSubjectUseCase {
    fun visit(actor: AcademicActor, subjectId: String, publicationIds: List<String>? = null): Map<String, Any?>
}
