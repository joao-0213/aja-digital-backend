package br.com.ajadigital.core.adapter.out.messaging



internal data class PendingOutboxEvent(
    val id: String,
    val type: String,
    val version: Int,
    val keyId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val attempts: Int,
)
