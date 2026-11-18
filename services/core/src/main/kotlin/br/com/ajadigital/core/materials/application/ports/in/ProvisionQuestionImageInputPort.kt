package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.ProvisionQuestionImageCommand

fun interface ProvisionQuestionImageInputPort {
    fun provisionQuestionImage(request: ProvisionQuestionImageCommand): FileMetadata
}
