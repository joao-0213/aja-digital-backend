package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

internal object AttemptPolicy {
    fun student(actor: AuthenticatedActor) {
        if (!actor.active || actor.role != "student" || actor.emailConfirmedAt == null) throw AcademicForbidden()
    }

    fun reader(actor: AuthenticatedActor) {
        if (!actor.active || actor.role !in setOf("student", "teacher", "admin") || actor.role == "student" && actor.emailConfirmedAt == null) {
            throw AcademicForbidden()
        }
    }

    fun validKey(value: String) {
        require(value.length in 8..128)
    }

    fun id(value: String) {
        require(validId(value))
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

}
