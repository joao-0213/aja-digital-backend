package br.com.ajadigital.core.dashboard.application.models

data class DashboardAssessment(
    val id: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String?,
    val moduleId: String,
    val title: String,
    val description: String,
    val kind: String,
    val maxGrade: Double?,
    val deadline: String?,
    val required: Boolean,
    val allowLate: Boolean,
    val status: String,
    val publishedAt: String,
    val questionCount: Int,
    val assigned: Boolean?,
)
