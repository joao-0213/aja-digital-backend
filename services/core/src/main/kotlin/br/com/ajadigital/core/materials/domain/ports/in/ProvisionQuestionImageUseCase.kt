package br.com.ajadigital.core.materials.domain.ports.`in`

interface ProvisionQuestionImageUseCase {
    fun provisionQuestionImage(id: String, questionId: String, name: String, mime: String, bytes: ByteArray): Map<String, Any?>
}
