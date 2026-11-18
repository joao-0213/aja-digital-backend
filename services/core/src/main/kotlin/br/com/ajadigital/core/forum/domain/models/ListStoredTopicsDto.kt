package br.com.ajadigital.core.forum.domain.models

data class ListStoredTopicsDto(val actorId: String, val role: String, val sessionHash: String, val page: Int, val size: Int, val sort: String, val classId: String?, val subjectId: String?, val query: String?, val unread: Boolean?, val solved: Boolean?)
