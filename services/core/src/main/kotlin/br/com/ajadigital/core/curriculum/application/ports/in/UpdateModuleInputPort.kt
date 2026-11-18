package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.UpdateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.Module

interface UpdateModuleInputPort {
    fun updateModule(request: UpdateModuleCommand): Module
}
