package br.com.ajadigital.core.ports.out

import java.time.Instant


data class AssessmentDraft(
    val moduleId: String,
    val title: String,
    val description: String,
    val kind: String,
    val maxGrade: Double?,
    val deadline: Instant?,
    val required: Boolean,
    val allowLate: Boolean,
)
