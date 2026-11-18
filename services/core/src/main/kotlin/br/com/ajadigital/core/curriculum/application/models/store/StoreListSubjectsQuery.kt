package br.com.ajadigital.core.curriculum.application.models.store

data class StoreListSubjectsQuery(val actorId: String, val role: String, val page: Int, val size: Int, val sort: String, val classId: String?, val query: String?)
