package br.com.ajadigital.core.dashboard.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetDashboardUseCase {
    fun dashboard(actor: AcademicActor): Map<String, Any?>
}
