package br.com.ajadigital.core.assessment.application.contracts

import java.time.Instant

data class DashboardAssessment(
    val id: String,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val updatedBy: String?,
    val moduleId: String,
    val title: String,
    val description: String,
    val kind: String,
    val maxGrade: Double?,
    val deadline: Instant?,
    val required: Boolean,
    val allowLate: Boolean,
    val publishedAt: Instant,
    val questionCount: Int,
    val assigned: Boolean?,
)
