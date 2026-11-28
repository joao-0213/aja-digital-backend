package br.com.ajadigital.core.outbox.application.ports.out

import br.com.ajadigital.core.outbox.application.contracts.EncryptedOutboxPayload
import br.com.ajadigital.core.outbox.application.contracts.PrepareIdentityNotificationCommand
import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto

interface OutboxPayloadProtectorOutputPort {
    fun encrypt(request: EncryptOutboxPayloadDto): EncryptedOutboxPayload
    fun encryptIdentityNotification(request: PrepareIdentityNotificationCommand): EncryptedOutboxPayload
}
