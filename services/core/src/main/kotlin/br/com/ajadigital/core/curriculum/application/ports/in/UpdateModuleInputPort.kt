package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.UpdateModuleDto

interface UpdateModuleInputPort {
    fun updateModule(request: UpdateModuleDto): Map<String, Any?>
}
