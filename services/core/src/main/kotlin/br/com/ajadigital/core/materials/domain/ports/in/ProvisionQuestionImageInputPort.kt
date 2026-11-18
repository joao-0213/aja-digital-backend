package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.domain.models.ProvisionQuestionImageDto

interface ProvisionQuestionImageInputPort {
    fun provisionQuestionImage(request: ProvisionQuestionImageDto): Map<String, Any?>
}
