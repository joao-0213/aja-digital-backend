package br.com.ajadigital.core.ports.out



data class AssessmentQuestion(
    val id: String,
    val source: String,
    val snapshot: Map<String, Any?>?,
    val sourceRevision: Int? = null,
    val contentHash: String? = null,
)
