package br.com.ajadigital.core.announcements.application.models

data class ListAnnouncementCalendarQuery(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val page: Int,
    val size: Int,
    val sort: String,
    val from: String?,
    val to: String?,
    val subjectId: String?,
)
