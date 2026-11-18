package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.CreateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.Module

interface CreateModuleInputPort {
    fun createModule(request: CreateModuleCommand): Module
}
