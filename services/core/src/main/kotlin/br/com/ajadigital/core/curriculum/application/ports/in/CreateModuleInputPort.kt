package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.CreateModuleDto

interface CreateModuleInputPort {
    fun createModule(request: CreateModuleDto): Map<String, Any?>
}
