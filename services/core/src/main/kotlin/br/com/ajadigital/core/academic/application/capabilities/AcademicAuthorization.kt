package br.com.ajadigital.core.academic.application.capabilities

import br.com.ajadigital.core.academic.application.models.AcademicStoreIsCurrentAdminSessionDto
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

internal object AcademicAuthorization {
    fun requireAdmin(store: AcademicStoreOutputPort, actor: AuthenticatedActor, serializeAdminInvariant: Boolean = false): AuthenticatedActor {
        AcademicPolicy.requireAdmin(actor.role, actor.active)
        if (!store.isCurrentAdminSession(AcademicStoreIsCurrentAdminSessionDto(actor.id, actor.sessionValidationReference, serializeAdminInvariant))) {
            throw AcademicForbidden()
        }
        return actor
    }
}
