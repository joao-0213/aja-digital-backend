package br.com.ajadigital.core.academic.application.models


data class AcademicStoreListUsersDto(val page: Int, val size: Int, val sort: String, val role: String?, val active: Boolean?, val query: String?)
