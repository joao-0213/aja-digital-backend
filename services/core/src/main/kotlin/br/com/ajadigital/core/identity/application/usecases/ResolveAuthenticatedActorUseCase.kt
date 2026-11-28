package br.com.ajadigital.core.identity.application.usecases

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveSessionInputPort

/** Keeps the full session record inside identity while exporting only actor facts. */
class ResolveAuthenticatedActorUseCase(
    private val resolveSession: ResolveSessionInputPort,
) : ResolveAuthenticatedActorInputPort {
    override fun resolve(sessionCookie: String?): AuthenticatedActor =
        resolveSession.resolve(sessionCookie).let { session ->
            AuthenticatedActor(
                session.user.id,
                session.user.role,
                session.user.active,
                session.user.emailConfirmedAt,
                session.tokenHash,
            )
        }
}
