package br.com.ajadigital.core.announcements.domain.models

data class ListAnnouncementsRecordDto(val actorId: String, val role: String, val sessionHash: String, val page: Int, val size: Int, val sort: String, val classId: String?, val subjectId: String?, val query: String?)
