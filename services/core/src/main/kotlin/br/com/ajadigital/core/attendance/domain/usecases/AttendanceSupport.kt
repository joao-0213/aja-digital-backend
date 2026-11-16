package br.com.ajadigital.core.attendance.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal object AttendanceSupport {
    fun readable(actor: AcademicActor) {
        if (!actor.active || actor.emailConfirmedAt == null || actor.role !in setOf("admin", "teacher", "student")) throw AcademicForbidden()
    }

    fun writable(actor: AcademicActor) {
        if (!actor.active || actor.emailConfirmedAt == null || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden()
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

    fun checkedId(value: String) = value.also { require(validId(it)) }

    fun date(value: String) = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException()
    }

    fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
