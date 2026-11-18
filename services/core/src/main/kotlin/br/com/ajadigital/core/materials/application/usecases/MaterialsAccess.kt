package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsForbidden

internal fun AuthenticatedActor.requireSessionRole() {
    if (!active || role !in setOf("admin", "teacher", "student")) throw MaterialsForbidden()
}

internal fun AuthenticatedActor.requireReader() {
    requireSessionRole()
    if (role == "student" && emailConfirmedAt == null) throw MaterialsForbidden()
}

internal fun AuthenticatedActor.requireWriter() {
    if (!active || role !in setOf("admin", "teacher")) throw MaterialsForbidden()
}
