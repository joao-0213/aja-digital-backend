package br.com.ajadigital.core.curriculum.domain.models

data class ListSubjectsRecordDto(val actorId: String, val role: String, val sessionHash: String, val page: Int, val size: Int, val sort: String, val classId: String?, val query: String?)
