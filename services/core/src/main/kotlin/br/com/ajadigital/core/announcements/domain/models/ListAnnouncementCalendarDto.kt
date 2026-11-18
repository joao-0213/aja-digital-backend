package br.com.ajadigital.core.announcements.domain.models

data class ListAnnouncementCalendarDto(val actorId: String, val role: String, val sessionHash: String, val page: Int, val size: Int, val sort: String, val from: String?, val to: String?, val subjectId: String?)
