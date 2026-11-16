package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ListDashboardClassesUseCase {
    fun dashboardClasses(actor: AcademicActor, pageSize: Int = 100): AcademicPage
}
