package br.com.ajadigital.core.academic.application.ports.`in`

import br.com.ajadigital.core.academic.application.contracts.DashboardAcademicProjection
import br.com.ajadigital.core.academic.application.contracts.DashboardAcademicQuery

fun interface GetDashboardAcademicInputPort {
    fun dashboard(query: DashboardAcademicQuery): DashboardAcademicProjection
}
