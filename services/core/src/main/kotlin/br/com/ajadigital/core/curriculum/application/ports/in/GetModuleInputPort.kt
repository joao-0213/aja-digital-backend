package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.GetModuleDto

interface GetModuleInputPort {
    fun module(request: GetModuleDto): Map<String, Any?>
}
