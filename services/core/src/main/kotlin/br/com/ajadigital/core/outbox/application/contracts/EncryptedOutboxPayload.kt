package br.com.ajadigital.core.outbox.application.contracts

class EncryptedOutboxPayload(
    val keyId: String,
    nonce: ByteArray,
    ciphertext: ByteArray,
) {
    private val nonceBytes = nonce.copyOf()
    private val ciphertextBytes = ciphertext.copyOf()
    val nonce: ByteArray get() = nonceBytes.copyOf()
    val ciphertext: ByteArray get() = ciphertextBytes.copyOf()
}
