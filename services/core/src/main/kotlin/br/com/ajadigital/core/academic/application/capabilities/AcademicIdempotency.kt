package br.com.ajadigital.core.academic.application.capabilities

import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto

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
        require(key.length in MIN_IDEMPOTENCY_KEY_LENGTH..MAX_IDEMPOTENCY_KEY_LENGTH)
        val request = FindIdempotencyDto(actor.id, operation, target, key, fingerprint())
        try {
            idempotency.findEncoded(request)?.let { return decode(it) }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        return command().also { idempotency.saveEncoded(request, encode(it)) }
    }
}

private const val MIN_IDEMPOTENCY_KEY_LENGTH = 8
private const val MAX_IDEMPOTENCY_KEY_LENGTH = 128
