package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListSubjectsUseCase {
    fun subjects(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, query: String?): AcademicPage
}
