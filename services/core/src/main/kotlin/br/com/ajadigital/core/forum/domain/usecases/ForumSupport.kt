package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import java.security.MessageDigest

internal object ForumSupport {
    fun readable(actor: AcademicActor): AcademicActor =
        actor.takeIf { it.active && it.role in setOf("admin", "teacher", "student") && (it.role != "student" || it.emailConfirmedAt != null) }
            ?: throw AcademicForbidden()

    fun writable(actor: AcademicActor): AcademicActor = readable(actor)

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

    fun checkedId(value: String) = value.also { require(validId(it)) }

    fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
