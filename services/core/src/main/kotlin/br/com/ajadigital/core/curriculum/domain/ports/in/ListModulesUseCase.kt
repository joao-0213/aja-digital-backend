package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListModulesUseCase {
    fun modules(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?): AcademicPage
}
