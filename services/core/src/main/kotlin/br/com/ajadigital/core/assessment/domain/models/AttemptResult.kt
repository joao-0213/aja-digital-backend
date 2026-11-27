package br.com.ajadigital.core.assessment.domain.models

class AttemptResult(
    val attempt: AttemptRecord,
    val receipt: AttemptReceipt,
    val score: Double,
    val maxScore: Double,
    questions: List<AttemptQuestion>,
) {
    val questions: List<AttemptQuestion> = java.util.Collections.unmodifiableList(questions.toList())
}
