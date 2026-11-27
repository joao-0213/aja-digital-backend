package br.com.ajadigital.core.academic.application.capabilities

import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import java.security.MessageDigest

internal object AcademicIdempotency {
    fun <T> execute(
        idempotency: EncodedIdempotencyStoreOutputPort,
        serializer: JsonSerializerOutputPort,
        actor: AuthenticatedActor,
        operation: String,
        target: String,
        key: String,
        body: Any,
        encode: (T) -> String,
        decode: (String) -> T,
        command: () -> T,
    ): T {
        require(key.length in 8..128)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(serializer.serialize(SerializeJsonDto(body)))
            .joinToString("") { "%02x".format(it) }
        try {
            val request = FindIdempotencyDto(actor.id, operation, target, key, fingerprint)
            idempotency.findEncoded(request)?.let { return decode(it) }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        val request = FindIdempotencyDto(actor.id, operation, target, key, fingerprint)
        return command().also { idempotency.saveEncoded(request, encode(it)) }
    }
}
