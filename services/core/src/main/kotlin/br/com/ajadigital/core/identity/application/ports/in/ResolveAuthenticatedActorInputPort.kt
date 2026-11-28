package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

/** Resolves only the published actor facts required by other feature applications. */
interface ResolveAuthenticatedActorInputPort {
    fun resolve(sessionCookie: String?): AuthenticatedActor
}
