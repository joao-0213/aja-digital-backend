package br.com.ajadigital.core.academic.application.models

data class AcademicStoreListStudentsDto(val actorId: String, val role: String, val page: Int, val size: Int, val sort: String, val classId: String?, val query: String?, val history: Boolean)
