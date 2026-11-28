package br.com.ajadigital.core.academic.application.capabilities

import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort

internal object AcademicIdempotency {
    fun <T> execute(
        idempotency: EncodedIdempotencyStoreOutputPort,
        actor: AuthenticatedActor,
        operation: String,
        target: String,
        key: String,
        fingerprint: () -> String,
        encode: (T) -> String,
        decode: (String) -> T,
        command: () -> T,
    ): T {
        require(key.length in 8..128)
        val request = FindIdempotencyDto(actor.id, operation, target, key, fingerprint())
        try {
            idempotency.findEncoded(request)?.let { return decode(it) }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        return command().also { idempotency.saveEncoded(request, encode(it)) }
    }
}
