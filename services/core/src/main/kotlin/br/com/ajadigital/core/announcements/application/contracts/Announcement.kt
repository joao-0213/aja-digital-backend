package br.com.ajadigital.core.announcements.application.contracts

data class Announcement(
    val id: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String,
    val classId: String,
    val subjectId: String?,
    val title: String,
    val body: String,
    val notify: Boolean,
    val occurrence: AnnouncementOccurrenceProjection?,
    val author: AnnouncementAuthor,
)
