package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.domain.models.ContentStoreProvisionQuestionImageDto

import br.com.ajadigital.core.materials.domain.models.ProvisionQuestionImageDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.materials.application.ports.`in`.ProvisionQuestionImageInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class ProvisionQuestionImageUseCase(private val store: ContentStoreOutputPort) : ProvisionQuestionImageInputPort {
    override fun provisionQuestionImage(request: ProvisionQuestionImageDto): Map<String, Any?> {
                val (id, questionId, name, mime, bytes) = request
        require(MaterialsPolicy.validId(id) && MaterialsPolicy.validId(questionId) && name.isNotBlank() && name.length <= 255 && name.none { it.isISOControl() } && '/' !in name && '\\' !in name)
        val normalized = MaterialsPolicy.normalizedMime(mime)
        require(bytes.size in 1..(5 * 1024 * 1024) && normalized in setOf("image/png", "image/jpeg", "image/webp") && MaterialsPolicy.validForumFile(name, normalized, bytes))
        return store.provisionQuestionImage(ContentStoreProvisionQuestionImageDto(id, questionId, name, normalized, bytes))
    }
}
