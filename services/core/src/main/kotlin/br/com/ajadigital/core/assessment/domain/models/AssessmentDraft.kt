package br.com.ajadigital.core.assessment.domain.models

import java.time.Instant

data class AssessmentDraft(
    val moduleId: String,
    val title: String,
    val description: String,
    val kind: AssessmentKind,
    val maxGrade: Double?,
    val deadline: Instant?,
    val required: Boolean,
    val allowLate: Boolean,
)
