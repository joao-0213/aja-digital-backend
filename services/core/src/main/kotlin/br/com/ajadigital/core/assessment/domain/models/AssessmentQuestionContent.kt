package br.com.ajadigital.core.assessment.domain.models

import java.util.Collections

class AssessmentQuestionContent(
    val subject: String,
    val topic: String,
    val difficulty: String,
    val institution: String,
    val year: Int,
    val statement: String,
    options: List<String>,
    val image: AssessmentQuestionImage?,
    val correct: Int,
    val explanation: String,
) {
    val options: List<String> = Collections.unmodifiableList(options.toList())

    fun withImage(image: AssessmentQuestionImage) = AssessmentQuestionContent(
        subject, topic, difficulty, institution, year, statement, options, image, correct, explanation,
    )

    override fun equals(other: Any?) = other is AssessmentQuestionContent &&
        subject == other.subject && topic == other.topic && difficulty == other.difficulty &&
        institution == other.institution && year == other.year && statement == other.statement &&
        options == other.options && image == other.image && correct == other.correct && explanation == other.explanation

    override fun hashCode() = listOf(subject, topic, difficulty, institution, year, statement, options, image, correct, explanation).hashCode()
}
