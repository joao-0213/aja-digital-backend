package br.com.ajadigital.core.assessment.domain.models

class AttemptQuestion(
    val id: String,
    fields: List<AttemptQuestionField>,
    val year: Int?,
    val image: AttemptQuestionImage?,
    val topic: String?,
    val correct: Int?,
    options: List<String>?,
    val subject: String?,
    val statement: String?,
    val difficulty: String?,
    val explanation: String?,
    val institution: String?,
) {
    val fields: List<AttemptQuestionField> = java.util.Collections.unmodifiableList(fields.toList())
    val options: List<String>? = options?.let { java.util.Collections.unmodifiableList(it.toList()) }
}
