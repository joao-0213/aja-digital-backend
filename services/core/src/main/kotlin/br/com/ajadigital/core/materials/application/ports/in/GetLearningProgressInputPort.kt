package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.contracts.LearningProgress
import br.com.ajadigital.core.materials.application.models.GetLearningProgressQuery
import br.com.ajadigital.core.shared.application.contracts.Page

fun interface GetLearningProgressInputPort {
    fun progress(request: GetLearningProgressQuery): Page<LearningProgress>
}
