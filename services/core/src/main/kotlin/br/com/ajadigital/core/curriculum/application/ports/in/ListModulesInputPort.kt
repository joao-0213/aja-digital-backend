package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.ListModulesDto
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

interface ListModulesInputPort {
    fun modules(request: ListModulesDto): AcademicPage
}
