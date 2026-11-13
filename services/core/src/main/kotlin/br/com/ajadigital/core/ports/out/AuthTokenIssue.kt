package br.com.ajadigital.core.ports.out

import java.time.Instant


data class AuthTokenIssue(
    val id: String,
    val userId: String,
    val purpose: String,
    val tokenHash: String,
    val emailAtIssue: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val eventId: String,
    val eventType: String,
    val encryptedPayload: ByteArray,
    val nonce: ByteArray,
    val keyId: String,
)
