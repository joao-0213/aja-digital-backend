package br.com.ajadigital.core.academic.application.models

import java.time.Instant

data class ProvisionedIdentity(
    val id: String,
    val actorId: String,
    val name: String,
    val email: String,
    val role: String,
    val now: Instant,
    val passwordHash: String,
    val passwordTokenId: String,
    val passwordTokenHash: String,
    val passwordEventId: String,
    val passwordCiphertext: ByteArray,
    val passwordNonce: ByteArray,
    val confirmationTokenId: String,
    val confirmationTokenHash: String,
    val confirmationEventId: String,
    val confirmationCiphertext: ByteArray,
    val confirmationNonce: ByteArray,
    val keyId: String,
)
