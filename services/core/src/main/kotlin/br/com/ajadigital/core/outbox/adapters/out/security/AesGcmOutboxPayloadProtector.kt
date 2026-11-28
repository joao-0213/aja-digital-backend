package br.com.ajadigital.core.outbox.adapters.out.security

import br.com.ajadigital.core.outbox.application.contracts.EncryptedOutboxPayload
import br.com.ajadigital.core.outbox.application.contracts.PrepareIdentityNotificationCommand
import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto
import tools.jackson.databind.ObjectMapper
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesGcmOutboxPayloadProtector(keyBase64: String, private val mapper: ObjectMapper) : OutboxPayloadProtectorOutputPort {
    private val key = Base64.getDecoder().decode(keyBase64).also { require(it.size == 32) }
    private val keyId: String = MessageDigest.getInstance("SHA-256").digest(key).take(8).joinToString("") { "%02x".format(it) }

    override fun encryptIdentityNotification(request: PrepareIdentityNotificationCommand): EncryptedOutboxPayload = encrypt(
        EncryptOutboxPayloadDto(
            request.eventId,
            request.eventType,
            request.eventVersion,
            mapper.writeValueAsBytes(mapOf("recipient" to request.email, "token" to request.token, "userId" to request.userId, "purpose" to request.purpose)),
        ),
    )


    override fun encrypt(request: EncryptOutboxPayloadDto): EncryptedOutboxPayload {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("${request.eventId}|${request.eventType}|${request.version}".toByteArray())
        return EncryptedOutboxPayload(keyId, nonce, cipher.doFinal(request.plaintext))
    }
}
