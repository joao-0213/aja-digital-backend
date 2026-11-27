package br.com.ajadigital.core.assessment.application.models

class AttemptAnswerSaveCommand(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val id: String,
    val expectedRevision: Int,
    answers: Map<String, Int?>,
) {
    val answers: Map<String, Int?> = java.util.Collections.unmodifiableMap(LinkedHashMap(answers))
}
