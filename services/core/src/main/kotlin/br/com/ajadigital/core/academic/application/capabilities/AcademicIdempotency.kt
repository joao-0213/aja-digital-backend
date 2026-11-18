package br.com.ajadigital.core.academic.application.capabilities

import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import java.security.MessageDigest

internal object AcademicIdempotency {
    fun execute(
        idempotency: IdempotencyStoreOutputPort,
        serializer: JsonSerializerOutputPort,
        actor: AuthenticatedActor,
        operation: String,
        target: String,
        key: String,
        body: Any,
        command: () -> Map<String, Any?>,
    ): Map<String, Any?> {
        require(key.length in 8..128)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(serializer.serialize(SerializeJsonDto(body)))
            .joinToString("") { "%02x".format(it) }
        try {
            idempotency.find(FindIdempotencyDto(actor.id, operation, target, key, fingerprint))?.let { return it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        return command().also { idempotency.save(SaveIdempotencyDto(actor.id, operation, target, key, fingerprint, it)) }
    }
}
