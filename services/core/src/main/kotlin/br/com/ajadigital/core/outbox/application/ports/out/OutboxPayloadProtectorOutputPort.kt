package br.com.ajadigital.core.outbox.application.ports.out

import br.com.ajadigital.core.outbox.application.contracts.EncryptedOutboxPayload
import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto
import br.com.ajadigital.core.outbox.domain.models.OutboxPayloadKeyDto
import br.com.ajadigital.core.outbox.domain.models.EncryptIdentityNotificationDto

interface OutboxPayloadProtectorOutputPort {
    fun encrypt(request: EncryptOutboxPayloadDto): EncryptedOutboxPayload
    fun encryptIdentityNotification(request: EncryptIdentityNotificationDto): EncryptedOutboxPayload
    fun keyId(request: OutboxPayloadKeyDto): String
}
