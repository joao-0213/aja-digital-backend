package br.com.ajadigital.core.forum.domain.policies

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

internal object ForumPolicy {
    fun readable(actor: AuthenticatedActor): AuthenticatedActor =
        actor.takeIf { it.active && it.role in setOf("admin", "teacher", "student") && (it.role != "student" || it.emailConfirmedAt != null) }
            ?: throw AcademicForbidden()

    fun writable(actor: AuthenticatedActor): AuthenticatedActor = readable(actor)

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

    fun checkedId(value: String) = value.also { require(validId(it)) }

}
