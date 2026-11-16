package br.com.ajadigital.core.identity.domain.models



data class EncryptedOutboxPayload(val keyId: String, val nonce: ByteArray, val ciphertext: ByteArray)
