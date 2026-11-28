package br.com.ajadigital.core.academic.application.models

data class AcademicStoreIsCurrentAdminSessionDto(
    val actorId: String,
    val sessionTokenHash: String,
    val serializeAdminInvariant: Boolean,
)
