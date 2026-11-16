package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.models.DashboardContent

interface GetDashboardContentUseCase {
    fun dashboard(actor: AcademicActor): DashboardContent
}
