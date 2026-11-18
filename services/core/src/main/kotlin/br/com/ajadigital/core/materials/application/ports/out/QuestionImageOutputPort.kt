package br.com.ajadigital.core.materials.application.ports.out

import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.StoreQuestionImageCommand

fun interface QuestionImageOutputPort {
    fun provisionQuestionImage(request: StoreQuestionImageCommand): FileMetadata
}
