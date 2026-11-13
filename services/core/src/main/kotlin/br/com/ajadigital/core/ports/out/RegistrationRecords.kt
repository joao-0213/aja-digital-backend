package br.com.ajadigital.core.ports.out

import br.com.ajadigital.core.domain.identity.User
import java.time.Instant


data class RegistrationRecords(
    val user: User,
    val passwordHash: String,
    val tokenId: String,
    val tokenHash: String,
    val tokenExpiresAt: Instant,
    val eventId: String,
    val encryptedPayload: ByteArray,
    val nonce: ByteArray,
    val keyId: String,
)
