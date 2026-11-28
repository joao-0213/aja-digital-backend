package br.com.ajadigital.core.academic.application.models

data class AcademicStoreListEnrollmentsDto(
    val actorId: String,
    val role: String,
    val page: Int,
    val size: Int,
    val sort: String,
    val classId: String?,
    val studentId: String?,
    val status: List<String>?,
    val query: String?,
)
