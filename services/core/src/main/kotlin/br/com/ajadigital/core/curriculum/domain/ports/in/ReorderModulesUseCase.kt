package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ReorderModulesUseCase {
    fun reorder(actor: AcademicActor, subjectId: String, revision: Int, items: List<Pair<String, Int>>, page: Int, pageSize: Int): Map<String, Any?>
}
