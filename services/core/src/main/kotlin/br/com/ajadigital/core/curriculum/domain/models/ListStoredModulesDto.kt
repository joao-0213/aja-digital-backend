package br.com.ajadigital.core.curriculum.domain.models

data class ListStoredModulesDto(val actorId: String, val role: String, val sessionHash: String, val page: Int, val size: Int, val sort: String, val subjectId: String?)
