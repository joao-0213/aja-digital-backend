package br.com.ajadigital.core.assessment.domain.models

data class AssessmentQuestion(
    val id: String,
    val source: String,
    val snapshot: AssessmentQuestionContent?,
    val sourceRevision: Int? = null,
    val contentHash: String? = null,
)
