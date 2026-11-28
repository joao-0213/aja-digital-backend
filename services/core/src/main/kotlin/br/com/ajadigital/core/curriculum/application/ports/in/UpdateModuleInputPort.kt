package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.UpdateModuleCommand

interface UpdateModuleInputPort {
    fun updateModule(request: UpdateModuleCommand): Module
}
