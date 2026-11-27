package br.com.ajadigital.core.assessment.domain.models

import java.time.Instant

data class AssessmentRecord(
    val id: String,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val updatedBy: String?,
    val draft: AssessmentDraft,
    val state: AssessmentState,
)
