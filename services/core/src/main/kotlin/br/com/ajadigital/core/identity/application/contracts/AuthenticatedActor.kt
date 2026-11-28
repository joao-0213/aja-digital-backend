package br.com.ajadigital.core.identity.application.contracts

import java.time.Instant

/** Published identity facts; the opaque session reference is only for internal revalidation. */
class AuthenticatedActor internal constructor(
    val id: String,
    val role: String,
    val active: Boolean,
    val emailConfirmedAt: Instant?,
    internal val sessionValidationReference: String,
) {
    override fun toString(): String = "AuthenticatedActor(id=$id, role=$role, active=$active, emailConfirmedAt=$emailConfirmedAt, sessionValidationReference=[redacted])"
}
