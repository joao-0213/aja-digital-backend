package br.com.ajadigital.core.academic.application.models


data class AcademicStoreUpdateUserDto(val id: String, val actorId: String, val expectedRevision: Int, val name: String?, val email: String?)
