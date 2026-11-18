package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.GetLearningProgressDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface GetLearningProgressInputPort {
    fun progress(request: GetLearningProgressDto): Map<String, Any?>
}
