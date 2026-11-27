package br.com.ajadigital.core.announcements.application.contracts

import br.com.ajadigital.core.announcements.domain.models.AnnouncementOccurrence

sealed interface AnnouncementOccurrenceProjection {
    val date: String
    val allDay: Boolean

    data class WithTime(override val date: String, val time: String?, override val allDay: Boolean) : AnnouncementOccurrenceProjection
    data class WithoutTime(override val date: String, override val allDay: Boolean) : AnnouncementOccurrenceProjection

    fun value(): AnnouncementOccurrence = when (this) {
        is WithTime -> AnnouncementOccurrence(date, time, allDay)
        is WithoutTime -> AnnouncementOccurrence(date, null, allDay)
    }
}
