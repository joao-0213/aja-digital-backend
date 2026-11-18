package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.contracts.LearningProgress
import br.com.ajadigital.core.materials.application.models.RecordMaterialProgressCommand

fun interface RecordMaterialProgressInputPort {
    fun recordProgress(request: RecordMaterialProgressCommand): LearningProgress
}
