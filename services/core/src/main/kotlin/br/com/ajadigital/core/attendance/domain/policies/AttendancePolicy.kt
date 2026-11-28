package br.com.ajadigital.core.attendance.domain.policies

import br.com.ajadigital.core.attendance.domain.exceptions.AttendanceForbidden
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal object AttendancePolicy {
    fun readable(
        role: String,
        active: Boolean,
        emailConfirmed: Boolean,
    ) {
        if (!active || !emailConfirmed || role !in setOf("admin", "teacher", "student")) throw AttendanceForbidden()
    }

    fun writable(
        role: String,
        active: Boolean,
        emailConfirmed: Boolean,
    ) {
        if (!active || !emailConfirmed || role !in setOf("admin", "teacher")) throw AttendanceForbidden()
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

    fun checkedId(value: String) = value.also { require(validId(it)) }

    fun date(value: String) = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException()
    }
}
