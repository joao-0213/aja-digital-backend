package br.com.ajadigital.core.outbox

import br.com.ajadigital.core.outbox.application.contracts.EncryptedOutboxPayload
import br.com.ajadigital.core.outbox.application.contracts.AppendPreparedOutboxEventCommand
import br.com.ajadigital.core.outbox.application.contracts.PrepareOutboxEventCommand
import br.com.ajadigital.core.outbox.application.ports.out.InsertOutboxEventOutputPort
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.outbox.application.usecases.OutboxEventProducerUseCase
import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto
import br.com.ajadigital.core.outbox.domain.models.OutboxPayloadKeyDto
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class OutboxEventProducerTests {
    @Test
    fun `producer keeps event identity ciphertext and append time in its typed protocol`() {
        val payload = byteArrayOf(1, 2, 3)
        var encryptedRequest: EncryptOutboxPayloadDto? = null
        var appended: AppendPreparedOutboxEventCommand? = null
        val protector = object : OutboxPayloadProtectorOutputPort {
            override fun encrypt(request: EncryptOutboxPayloadDto): EncryptedOutboxPayload {
                encryptedRequest = request
                return EncryptedOutboxPayload("key-1", byteArrayOf(4), byteArrayOf(5, 6))
            }
            override fun keyId(request: OutboxPayloadKeyDto) = "key-1"
        }
        val writer = object : InsertOutboxEventOutputPort {
            override fun insert(request: AppendPreparedOutboxEventCommand) { appended = request }
        }
        val producer = OutboxEventProducerUseCase(protector, writer)
        val command = PrepareOutboxEventCommand("event-1", "academic.announcement-notification-requested", 1, payload)
        payload[0] = 9
        val prepared = producer.prepare(command)
        producer.append(AppendPreparedOutboxEventCommand(prepared, Instant.parse("2026-11-12T12:00:00Z")))

        assertEquals("event-1", encryptedRequest?.eventId)
        assertArrayEquals(byteArrayOf(1, 2, 3), encryptedRequest?.plaintext)
        assertEquals("event-1", appended?.event?.id)
        assertEquals("academic.announcement-notification-requested", appended?.event?.eventType)
        assertEquals(1, appended?.event?.eventVersion)
        assertArrayEquals(byteArrayOf(5, 6), appended?.event?.ciphertext)
        assertEquals(Instant.parse("2026-11-12T12:00:00Z"), appended?.createdAt)
    }
}
