package br.com.ajadigital.core.assessment.domain.models

enum class AssessmentKind(
    val value: String,
) {
    QUIZ("quiz"),
    WORK("work"),
    ;

    companion object {
        fun parse(value: String): AssessmentKind = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException()
    }
}
