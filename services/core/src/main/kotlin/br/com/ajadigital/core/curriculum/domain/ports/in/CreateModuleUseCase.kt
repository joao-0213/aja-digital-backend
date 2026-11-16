package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CreateModuleUseCase {
    fun createModule(actor: AcademicActor, subjectId: String, title: String, description: String, key: String): Map<String, Any?>
}
