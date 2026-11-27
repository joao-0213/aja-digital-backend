package br.com.ajadigital.core.announcements.application.contracts

data class AnnouncementCalendarEvent(
    val id: String,
    val title: String,
    val kind: String,
    val date: String,
    val time: String?,
    val allDay: Boolean,
    val description: String,
    val subjectId: String?,
    val source: String,
    val sourceId: String,
    val href: String,
)
