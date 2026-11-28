package br.com.ajadigital.core.materials.domain.models

import java.time.Instant

data class FilePreparation(
    val ownerId: String,
    val expiresAt: Instant,
) {
    fun isOwnedBy(
        actorId: String,
        now: Instant,
    ): Boolean = ownerId == actorId && expiresAt > now
}
