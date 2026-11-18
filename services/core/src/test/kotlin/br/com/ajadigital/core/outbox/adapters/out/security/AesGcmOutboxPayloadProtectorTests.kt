package br.com.ajadigital.core.outbox.adapters.out.security

import br.com.ajadigital.core.outbox.domain.models.EncryptOutboxPayloadDto
import br.com.ajadigital.core.outbox.domain.models.OutboxPayloadKeyDto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesGcmOutboxPayloadProtectorTests {
    @Test
    fun `encrypts payload with AES-GCM and AAD and computes 16-char hex keyId`() {
        val rawKey = ByteArray(32) { it.toByte() }
        val protector = AesGcmOutboxPayloadProtector(Base64.getEncoder().encodeToString(rawKey))
        val plaintext = """{"hello":"world"}""".toByteArray()

        val encrypted = protector.encrypt(EncryptOutboxPayloadDto("evt-1", "identity.student.registered", 1, plaintext))

        val keyId = protector.keyId(OutboxPayloadKeyDto)
        assertEquals(16, keyId.length)
        assertEquals(keyId, encrypted.keyId)
        assertEquals(12, encrypted.nonce.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(rawKey, "AES"), GCMParameterSpec(128, encrypted.nonce))
        cipher.updateAAD("evt-1|identity.student.registered|1".toByteArray())
        assertArrayEquals(plaintext, cipher.doFinal(encrypted.ciphertext))
    }

    @Test
    fun `rejects non-32-byte key`() {
        assertThrows(IllegalArgumentException::class.java) {
            AesGcmOutboxPayloadProtector(Base64.getEncoder().encodeToString(ByteArray(16)))
        }
    }
}
