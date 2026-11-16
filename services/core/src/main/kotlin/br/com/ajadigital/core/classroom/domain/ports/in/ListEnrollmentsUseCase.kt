package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListEnrollmentsUseCase {
    fun enrollments(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, studentId: String?, status: List<String>?, q: String?): AcademicPage
}
