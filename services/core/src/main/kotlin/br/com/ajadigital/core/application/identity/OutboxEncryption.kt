package br.com.ajadigital.core.application.identity

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec


class OutboxEncryption(keyBase64: String) {
    private val key = Base64.getDecoder().decode(keyBase64).also { require(it.size == 32) }
    val keyId = MessageDigest.getInstance("SHA-256").digest(key).take(8).joinToString("") { "%02x".format(it) }

    fun encrypt(eventId: String, eventType: String, version: Int, plaintext: ByteArray): EncryptedOutboxPayload {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("$eventId|$eventType|$version".toByteArray())
        return EncryptedOutboxPayload(keyId, nonce, cipher.doFinal(plaintext))
    }
}
