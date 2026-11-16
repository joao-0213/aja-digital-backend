package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.materials.domain.ports.`in`.ProvisionQuestionImageUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class ProvisionQuestionImage(private val store: ContentStore) : ProvisionQuestionImageUseCase {
    override fun provisionQuestionImage(id: String, questionId: String, name: String, mime: String, bytes: ByteArray): Map<String, Any?> {
        require(MaterialsSupport.validId(id) && MaterialsSupport.validId(questionId) && name.isNotBlank() && name.length <= 255 && name.none { it.isISOControl() } && '/' !in name && '\\' !in name)
        val normalized = MaterialsSupport.normalizedMime(mime)
        require(bytes.size in 1..(5 * 1024 * 1024) && normalized in setOf("image/png", "image/jpeg", "image/webp") && MaterialsSupport.validForumFile(name, normalized, bytes))
        return store.provisionQuestionImage(id, questionId, name, normalized, bytes)
    }
}
