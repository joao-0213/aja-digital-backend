package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto

/** Resolves only the published actor facts required by other feature applications. */
interface ResolveAuthenticatedActorInputPort {
    fun resolve(request: ResolveStudentSessionDto): AuthenticatedActor
}
