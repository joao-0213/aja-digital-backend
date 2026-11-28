package br.com.ajadigital.core.attendance.domain.policies

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal object AttendancePolicy {
    fun readable(actor: AuthenticatedActor) {
        if (!actor.active || actor.emailConfirmedAt == null || actor.role !in setOf("admin", "teacher", "student")) throw AcademicForbidden()
    }

    fun writable(actor: AuthenticatedActor) {
        if (!actor.active || actor.emailConfirmedAt == null || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden()
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

    fun checkedId(value: String) = value.also { require(validId(it)) }

    fun date(value: String) = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException()
    }

}
