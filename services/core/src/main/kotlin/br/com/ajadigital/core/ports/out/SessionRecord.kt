package br.com.ajadigital.core.ports.out

import br.com.ajadigital.core.domain.identity.User
import java.time.Instant


data class SessionRecord(
    val id: String,
    val tokenHash: String,
    val user: User,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
)
