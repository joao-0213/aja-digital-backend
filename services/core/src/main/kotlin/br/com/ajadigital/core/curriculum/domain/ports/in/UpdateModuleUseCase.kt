package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface UpdateModuleUseCase {
    fun updateModule(actor: AcademicActor, id: String, revision: Int, title: String?, description: String?): Map<String, Any?>
}
