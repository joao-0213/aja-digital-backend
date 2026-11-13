package br.com.ajadigital.core.ports.out



data class AnnouncementDraft(
    val classId: String,
    val subjectId: String?,
    val title: String,
    val body: String,
    val notify: Boolean,
    val occurrence: AnnouncementOccurrence?,
)
