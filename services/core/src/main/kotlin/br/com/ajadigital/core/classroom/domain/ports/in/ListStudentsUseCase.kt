package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListStudentsUseCase {
    fun students(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, q: String?, history: Boolean): AcademicPage
}
