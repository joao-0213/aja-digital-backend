package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.ListDashboardClassesDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.models.AcademicPage

interface ListDashboardClassesInputPort {
    fun dashboardClasses(request: ListDashboardClassesDto): AcademicPage
}
