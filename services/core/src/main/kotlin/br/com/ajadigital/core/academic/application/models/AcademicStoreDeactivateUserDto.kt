package br.com.ajadigital.core.academic.application.models

data class AcademicStoreDeactivateUserDto(
    val id: String,
    val actorId: String,
    val expectedRevision: Int,
)
