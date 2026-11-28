package br.com.ajadigital.core.outbox.application.usecases

import br.com.ajadigital.core.outbox.application.contracts.AppendPreparedOutboxEventCommand
import br.com.ajadigital.core.outbox.application.contracts.PrepareOutboxEventCommand
import br.com.ajadigital.core.outbox.application.contracts.PreparedOutboxEvent
import br.com.ajadigital.core.outbox.application.contracts.PrepareIdentityNotificationCommand
import br.com.ajadigital.core.outbox.application.contracts.EncryptedOutboxPayload
import br.com.ajadigital.core.outbox.application.ports.`in`.OutboxEventProducerInputPort
import br.com.ajadigital.core.outbox.application.ports.out.InsertOutboxEventOutputPort
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto

class OutboxEventProducerUseCase(
    private val protector: OutboxPayloadProtectorOutputPort,
    private val events: InsertOutboxEventOutputPort,
) : OutboxEventProducerInputPort {
    override fun prepare(request: PrepareOutboxEventCommand): PreparedOutboxEvent {
        val encrypted = protector.encrypt(EncryptOutboxPayloadDto(request.id, request.eventType, request.eventVersion, request.payload))
        return PreparedOutboxEvent(request.id, request.eventType, request.eventVersion, encrypted.keyId, encrypted.nonce, encrypted.ciphertext)
    }

    override fun prepareIdentityNotification(request: PrepareIdentityNotificationCommand): EncryptedOutboxPayload =
        protector.encryptIdentityNotification(request)

    override fun append(request: AppendPreparedOutboxEventCommand) {
        events.insert(request)
    }
}
