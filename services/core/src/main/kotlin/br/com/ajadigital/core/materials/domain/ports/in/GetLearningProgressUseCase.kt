package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetLearningProgressUseCase {
    fun progress(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?, materialId: String?): Map<String, Any?>
}
