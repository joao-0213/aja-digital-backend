package br.com.ajadigital.core.academic.application.models

data class AcademicStoreListClassesDto(
    val actorId: String,
    val role: String,
    val page: Int,
    val size: Int,
    val sort: String,
    val active: Boolean?,
    val query: String?,
)
