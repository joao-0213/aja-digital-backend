package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.ReorderModulesDto

interface ReorderModulesInputPort {
    fun reorder(request: ReorderModulesDto): Map<String, Any?>
}
