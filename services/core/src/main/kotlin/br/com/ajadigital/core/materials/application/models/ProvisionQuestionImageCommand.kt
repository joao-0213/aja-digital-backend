package br.com.ajadigital.core.materials.application.models

class ProvisionQuestionImageCommand(
    val id: String,
    val questionId: String,
    val name: String,
    val mime: String,
    bytes: ByteArray,
    val credential: QuestionServiceCredential,
) {
    internal val content = FileBytes(bytes)
}
