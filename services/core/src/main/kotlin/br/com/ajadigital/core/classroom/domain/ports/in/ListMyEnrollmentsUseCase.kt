package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListMyEnrollmentsUseCase {
    fun myEnrollments(actor: AcademicActor, page: Int, size: Int, sort: String): AcademicPage
}
