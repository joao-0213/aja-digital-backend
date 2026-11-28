package br.com.ajadigital.core.academic.application.models

data class AcademicStoreWithdrawUserDto(
    val id: String,
    val actorId: String,
    val expectedRevision: Int,
)
