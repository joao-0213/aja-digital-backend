package br.com.ajadigital.core.announcements.domain.models

data class AnnouncementOccurrence(
    val date: String,
    val time: String?,
    val allDay: Boolean,
)
