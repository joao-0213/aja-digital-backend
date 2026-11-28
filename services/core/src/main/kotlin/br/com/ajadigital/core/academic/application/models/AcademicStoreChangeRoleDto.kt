package br.com.ajadigital.core.academic.application.models

data class AcademicStoreChangeRoleDto(
    val id: String,
    val actorId: String,
    val expectedRevision: Int,
    val role: String,
)
