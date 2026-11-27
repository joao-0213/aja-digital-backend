package br.com.ajadigital.core.assessment.domain.models

class AttemptRecord(
    val id: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String?,
    val assessmentId: String,
    val assessmentTitle: String,
    val userId: String,
    val studentName: String?,
    val startedAt: String,
    val state: AttemptState,
    answers: Map<String, Int?>,
    questionOrder: List<String>,
    questions: List<AttemptPrompt>,
    val questionsUpdatedAt: String?,
) {
    val answers: Map<String, Int?> = java.util.Collections.unmodifiableMap(LinkedHashMap(answers))
    val questionOrder: List<String> = java.util.Collections.unmodifiableList(questionOrder.toList())
    val questions: List<AttemptPrompt> = java.util.Collections.unmodifiableList(questions.toList())
}
