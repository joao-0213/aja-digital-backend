package br.com.ajadigital.core.announcements.domain.policies

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementForbidden
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

internal object AnnouncementsPolicy {
    fun validate(draft: AnnouncementDraft) {
        require(draft.classId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")))
        require(draft.subjectId == null || draft.subjectId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")))
        require(draft.title.isNotBlank() && draft.title.length <= 160 && draft.body.length <= 10000)
        draft.occurrence?.let {
            parseDate(it.date)
            require(it.time == null || it.time.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")))
            it.time?.let(LocalTime::parse)
        }
    }

    fun requireReader(role: String, active: Boolean, emailConfirmed: Boolean) {
        if (!active || role !in setOf("admin", "teacher", "student") || role == "student" && !emailConfirmed) throw AnnouncementForbidden()
    }

    fun requireWriter(role: String, active: Boolean, emailConfirmed: Boolean) {
        if (!active || role !in setOf("admin", "teacher")) throw AnnouncementForbidden()
    }

    fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException()
    }

}
