package br.com.ajadigital.core.outbox.domain.models



data class PendingOutboxEvent(
    val id: String,
    val type: String,
    val version: Int,
    val keyId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val attempts: Int,
)
