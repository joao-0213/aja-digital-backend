package br.com.ajadigital.core.outbox

import br.com.ajadigital.core.outbox.application.contracts.EncryptedOutboxPayload
import br.com.ajadigital.core.outbox.application.contracts.AppendPreparedOutboxEventCommand
import br.com.ajadigital.core.outbox.application.contracts.PrepareOutboxEventCommand
import br.com.ajadigital.core.outbox.application.contracts.PrepareIdentityNotificationCommand
import br.com.ajadigital.core.outbox.application.ports.out.InsertOutboxEventOutputPort
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.outbox.application.usecases.OutboxEventProducerUseCase
import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class OutboxEventProducerTests {
    @Test
    fun `producer keeps event identity ciphertext and append time in its typed protocol`() {
        val payload = byteArrayOf(1, 2, 3)
        var encryptedRequest: EncryptOutboxPayloadDto? = null
        var encryptedNotification: PrepareIdentityNotificationCommand? = null
        var appended: AppendPreparedOutboxEventCommand? = null
        val protector = object : OutboxPayloadProtectorOutputPort {
            override fun encrypt(request: EncryptOutboxPayloadDto): EncryptedOutboxPayload {
                encryptedRequest = request
                return EncryptedOutboxPayload("key-1", byteArrayOf(4), byteArrayOf(5, 6))
            }
            override fun encryptIdentityNotification(request: PrepareIdentityNotificationCommand): EncryptedOutboxPayload {
                encryptedNotification = request
                return EncryptedOutboxPayload("key-1", byteArrayOf(7), byteArrayOf(8))
            }
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

        val tokenCommand = PrepareIdentityNotificationCommand("token-event", "identity.email-confirmation-requested", 1, "a@example.test", "secret-token", "user-1", "email_confirmation")
        val encryptedToken = producer.prepareIdentityNotification(tokenCommand)
        assertEquals(tokenCommand, encryptedNotification)
        assertEquals("key-1", encryptedToken.keyId)
        assertEquals(false, tokenCommand.toString().contains("secret-token"))

        val nonce = byteArrayOf(1)
        val ciphertext = byteArrayOf(2)
        val encryptedValue = EncryptedOutboxPayload("key-1", nonce, ciphertext)
        nonce[0] = 9
        ciphertext[0] = 9
        encryptedValue.nonce[0] = 8
        encryptedValue.ciphertext[0] = 8
        assertArrayEquals(byteArrayOf(1), encryptedValue.nonce)
        assertArrayEquals(byteArrayOf(2), encryptedValue.ciphertext)
    }
}
