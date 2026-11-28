package br.com.ajadigital.core.announcements.application.models

data class ListAnnouncementsStoreQuery(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val page: Int,
    val size: Int,
    val sort: String,
    val classId: String?,
    val subjectId: String?,
    val query: String?,
)
