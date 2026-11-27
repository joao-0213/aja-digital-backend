package br.com.ajadigital.core.outbox.application.contracts

data class EncryptedOutboxPayload(val keyId: String, val nonce: ByteArray, val ciphertext: ByteArray)
