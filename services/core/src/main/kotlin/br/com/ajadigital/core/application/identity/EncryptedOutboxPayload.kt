package br.com.ajadigital.core.application.identity



data class EncryptedOutboxPayload(val keyId: String, val nonce: ByteArray, val ciphertext: ByteArray)
