package br.com.ajadigital.core.academic.application.models

data class AcademicStudentSummaryView(
    val id: String,
    val name: String,
    val email: String,
    val active: Boolean,
    val enrollment: AcademicEnrollmentView?,
)
