package br.com.ajadigital.core.announcements.domain.models

data class AnnouncementDraft(
    val classId: String,
    val subjectId: String?,
    val title: String,
    val body: String,
    val notify: Boolean,
    val occurrence: AnnouncementOccurrence?,
)
