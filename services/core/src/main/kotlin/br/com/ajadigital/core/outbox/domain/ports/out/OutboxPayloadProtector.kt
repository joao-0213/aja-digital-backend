package br.com.ajadigital.core.outbox.domain.ports.out

import br.com.ajadigital.core.identity.domain.models.EncryptedOutboxPayload

interface OutboxPayloadProtector {
    val keyId: String
    fun encrypt(eventId: String, eventType: String, version: Int, plaintext: ByteArray): EncryptedOutboxPayload
}
