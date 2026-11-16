package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListMaterialsUseCase {
    fun materials(actor: AcademicActor, page: Int, size: Int, sort: String, moduleId: String?, subjectId: String?): AcademicPage
}
