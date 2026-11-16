package br.com.ajadigital.core.dashboard.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ManageDashboardUseCase {
    fun dashboard(actor: AcademicActor): Map<String, Any?>
}
