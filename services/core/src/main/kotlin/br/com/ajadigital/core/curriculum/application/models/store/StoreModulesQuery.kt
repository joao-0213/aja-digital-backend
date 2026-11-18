package br.com.ajadigital.core.curriculum.application.models.store

data class StoreModulesQuery(val actorId: String, val role: String, val page: Int, val size: Int, val sort: String, val subjectId: String?)
