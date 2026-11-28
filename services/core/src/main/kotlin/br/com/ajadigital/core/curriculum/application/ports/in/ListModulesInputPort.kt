package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.ListModulesQuery
import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.shared.application.contracts.Page

interface ListModulesInputPort {
    fun modules(request: ListModulesQuery): Page<Module>
}
