package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.ReorderModulesCommand
import br.com.ajadigital.core.shared.application.contracts.Page
import br.com.ajadigital.core.curriculum.application.models.Module

interface ReorderModulesInputPort {
    fun reorder(request: ReorderModulesCommand): Page<Module>
}
