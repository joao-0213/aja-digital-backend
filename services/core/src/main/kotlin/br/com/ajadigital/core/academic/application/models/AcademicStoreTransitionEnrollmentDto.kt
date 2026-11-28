package br.com.ajadigital.core.academic.application.models

data class AcademicStoreTransitionEnrollmentDto(
    val id: String,
    val actorId: String,
    val expectedRevision: Int,
    val command: String,
)
