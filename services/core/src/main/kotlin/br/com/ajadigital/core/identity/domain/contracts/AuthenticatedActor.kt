package br.com.ajadigital.core.identity.domain.contracts

import java.time.Instant

/**
 * Transitional immutable identity facts exported to legacy domain callers.
 * The opaque reference exists only for current-session revalidation and is never serialized or logged.
 */
class AuthenticatedActor internal constructor(
    val id: String,
    val role: String,
    val active: Boolean,
    val emailConfirmedAt: Instant?,
    internal val sessionValidationReference: String,
) {
    override fun toString(): String =
        "AuthenticatedActor(id=$id, role=$role, active=$active, emailConfirmedAt=$emailConfirmedAt, sessionValidationReference=[redacted])"
}
