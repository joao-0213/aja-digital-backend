package br.com.ajadigital.core.academic.application.models

data class AcademicStoreUpdateClassDto(val id: String, val actorId: String, val expectedRevision: Int, val name: String?, val code: String?, val year: Int?, val shift: String?)
