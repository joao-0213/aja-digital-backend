package br.com.ajadigital.core.assessment.application.models

class SaveAttemptAnswersBody(
    val expectedRevision: Int?,
    answers: Map<String, Int?>?,
    val fieldsValid: Boolean,
    val answersValid: Boolean,
) {
    val answers: Map<String, Int?>? = answers?.let { java.util.Collections.unmodifiableMap(LinkedHashMap(it)) }
}
