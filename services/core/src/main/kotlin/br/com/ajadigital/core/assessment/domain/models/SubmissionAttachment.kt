package br.com.ajadigital.core.assessment.domain.models

data class SubmissionAttachment(
    val kind: String,
    val name: String,
    val fileId: String? = null,
    val url: String? = null,
)
